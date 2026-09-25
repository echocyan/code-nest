package com.echocyan.codenest.framework.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.framework.ratelimit.SlidingWindowRateLimiter.Quota;
import com.echocyan.codenest.support.IntegrationTest;
import com.echocyan.codenest.support.RateLimitEnabled;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 滑动窗口脚本的窗口边界。时间取自 Redis，测试按返回的等待时间睡眠，不依赖本机时钟。
 */
@RateLimitEnabled
class SlidingWindowRateLimiterTest extends IntegrationTest {

    private static final Duration WINDOW = Duration.ofMillis(800);

    @Autowired
    private SlidingWindowRateLimiter limiter;

    @Test
    void rejectsOnceWindowIsFullAndWaitsUntilOldestRecordLeaves() {
        Quota quota = new Quota(uniqueKey(), 2, WINDOW);

        assertThat(limiter.tryAcquire(List.of(quota))).isZero();
        assertThat(limiter.tryAcquire(List.of(quota))).isZero();
        long wait = limiter.tryAcquire(List.of(quota));

        assertThat(wait).isPositive().isLessThanOrEqualTo(WINDOW.toMillis());
    }

    @Test
    void windowSlidesInsteadOfResettingAtOnce() throws InterruptedException {
        Quota quota = new Quota(uniqueKey(), 2, WINDOW);
        assertThat(limiter.tryAcquire(List.of(quota))).isZero();
        Thread.sleep(WINDOW.toMillis() / 2);
        assertThat(limiter.tryAcquire(List.of(quota))).isZero();

        // 等到第一条记录滑出窗口：腾出一个名额，第二条仍在窗口内
        Thread.sleep(limiter.tryAcquire(List.of(quota)) + 20);

        assertThat(limiter.tryAcquire(List.of(quota))).isZero();
        assertThat(limiter.tryAcquire(List.of(quota))).isPositive();
    }

    @Test
    void rejectedRequestIsNotRecordedInAnyQuota() throws InterruptedException {
        Quota shortTerm = new Quota(uniqueKey(), 1, WINDOW);
        Quota longTerm = new Quota(uniqueKey(), 2, Duration.ofMinutes(1));
        List<Quota> both = List.of(shortTerm, longTerm);
        assertThat(limiter.tryAcquire(both)).isZero();

        long wait = limiter.tryAcquire(both);
        assertThat(wait).isPositive().isLessThanOrEqualTo(WINDOW.toMillis());
        Thread.sleep(wait + 20);

        // 被拒绝的那次没有记入长期额度，所以还剩一个名额
        assertThat(limiter.tryAcquire(both)).isZero();
        Thread.sleep(WINDOW.toMillis() + 20);
        // 长期额度用尽时，等待时间取最晚腾出名额的那个额度
        assertThat(limiter.tryAcquire(both)).isGreaterThan(WINDOW.toMillis());
    }

    private static String uniqueKey() {
        return "test:" + UUID.randomUUID();
    }
}
