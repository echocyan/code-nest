package com.echocyan.codenest.framework.ratelimit;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.framework.ratelimit.RateLimit.Dimension;
import com.echocyan.codenest.framework.ratelimit.RateLimitProperties.RuleProperties;
import com.echocyan.codenest.framework.ratelimit.RateLimitRules.Rule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流规则的解析与启动校验：接口上只写规则名，规则本身在配置里只有一份；规则名未配置、匿名接口按用户限流都阻止启动。
 */
class RateLimitRulesTest {

    private static final Map<String, RuleProperties> CONFIGURED = Map.of(
            "comment-per-minute", new RuleProperties(5, Duration.ofMinutes(1), Dimension.USER),
            "comment-per-day", new RuleProperties(100, Duration.ofDays(1), Dimension.USER),
            "search-per-minute", new RuleProperties(60, Duration.ofMinutes(1), Dimension.IP));

    private static Method method(Class<?> type, String name) {
        return Stream.of(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static RateLimitRules rulesOf(Class<?> type, String... methodNames) {
        return new RateLimitRules(Stream.of(methodNames).map(name -> method(type, name)).toList(), CONFIGURED);
    }

    @Test
    void endpointsSharingARuleNameShareTheConfiguredRule() {
        RateLimitRules rules = rulesOf(Endpoints.class, "comment", "reply", "search", "unlimited");

        Rule perMinute = new Rule("comment-per-minute", 5, Duration.ofMinutes(1), Dimension.USER);
        assertThat(rules.of(method(Endpoints.class, "comment"))).containsExactly(perMinute,
                new Rule("comment-per-day", 100, Duration.ofDays(1), Dimension.USER));
        assertThat(rules.of(method(Endpoints.class, "reply"))).containsExactly(perMinute);
        assertThat(rules.of(method(Endpoints.class, "search")))
                .containsExactly(new Rule("search-per-minute", 60, Duration.ofMinutes(1), Dimension.IP));
        assertThat(rules.of(method(Endpoints.class, "unlimited"))).isEmpty();
    }

    @Test
    void unconfiguredRuleNameIsRejected() {
        assertThatThrownBy(() -> rulesOf(Endpoints.class, "unconfigured"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("follow-per-minute")
                .hasMessageContaining("unconfigured");
    }

    @Test
    void perUserRuleOnAnonymousEndpointIsRejected() {
        assertThatThrownBy(() -> rulesOf(Endpoints.class, "anonymousComment"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment-per-minute")
                .hasMessageContaining("anonymousComment");
        assertThatThrownBy(() -> rulesOf(AnonymousEndpoints.class, "comment"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment-per-minute");
    }

    @SuppressWarnings("unused")
    private static class Endpoints {

        @RateLimit("comment-per-minute")
        @RateLimit("comment-per-day")
        void comment() {
        }

        @RateLimit("comment-per-minute")
        void reply() {
        }

        @SaIgnore
        @RateLimit("search-per-minute")
        void search() {
        }

        @RateLimit("follow-per-minute")
        void unconfigured() {
        }

        @SaIgnore
        @RateLimit("comment-per-minute")
        void anonymousComment() {
        }

        void unlimited() {
        }
    }

    @SaIgnore
    @SuppressWarnings("unused")
    private static class AnonymousEndpoints {

        @RateLimit("comment-per-minute")
        void comment() {
        }
    }
}
