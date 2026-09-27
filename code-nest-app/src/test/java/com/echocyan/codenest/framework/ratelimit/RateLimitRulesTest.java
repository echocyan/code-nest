package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension;
import com.echocyan.codenest.framework.ratelimit.RateLimitRules.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流规则的解析与一致性校验：不同接口标注的同一个额度名必须是同一条规则，否则共用的额度会按不同的规则计数。
 */
class RateLimitRulesTest {

    private final MockEnvironment environment = new MockEnvironment().withProperty("limits.comment", "5");

    private static Method method(String name) {
        return Stream.of(Endpoints.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private RateLimitRules rulesOf(String... methodNames) {
        return new RateLimitRules(Stream.of(methodNames).map(RateLimitRulesTest::method).toList(), environment);
    }

    @Test
    void placeholdersAreResolvedAndSharedKeysMayRepeat() {
        RateLimitRules rules = rulesOf("comment", "reply", "unlimited");

        Rule comment = new Rule("comment-per-minute", 5, Duration.ofMinutes(1), Dimension.USER);
        assertThat(rules.of(method("comment"))).containsExactly(comment,
                new Rule("comment-per-day", 100, Duration.ofDays(1), Dimension.USER));
        assertThat(rules.of(method("reply"))).containsExactly(comment);
        assertThat(rules.of(method("unlimited"))).isEmpty();
    }

    @Test
    void sameKeyWithDifferentLimitIsRejected() {
        assertThatThrownBy(() -> rulesOf("comment", "replyWithOwnLimit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment-per-minute")
                .hasMessageContaining("comment")
                .hasMessageContaining("replyWithOwnLimit");
    }

    @Test
    void sameKeyWithDifferentWindowIsRejected() {
        assertThatThrownBy(() -> rulesOf("comment", "replyWithOwnWindow"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment-per-minute");
    }

    @Test
    void sameKeyWithDifferentDimensionIsRejected() {
        assertThatThrownBy(() -> rulesOf("comment", "replyByIp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment-per-minute");
    }

    @SuppressWarnings("unused")
    private static class Endpoints {

        @RateLimit(key = "comment-per-minute", limit = "${limits.comment}", window = "1m", dimension = Dimension.USER)
        @RateLimit(key = "comment-per-day", limit = "100", window = "1d", dimension = Dimension.USER)
        void comment() {
        }

        @RateLimit(key = "comment-per-minute", limit = "5", window = "60s", dimension = Dimension.USER)
        void reply() {
        }

        @RateLimit(key = "comment-per-minute", limit = "6", window = "1m", dimension = Dimension.USER)
        void replyWithOwnLimit() {
        }

        @RateLimit(key = "comment-per-minute", limit = "5", window = "1h", dimension = Dimension.USER)
        void replyWithOwnWindow() {
        }

        @RateLimit(key = "comment-per-minute", limit = "5", window = "1m", dimension = Dimension.IP)
        void replyByIp() {
        }

        void unlimited() {
        }
    }
}
