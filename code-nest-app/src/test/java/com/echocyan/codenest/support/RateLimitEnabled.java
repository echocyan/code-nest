package com.echocyan.codenest.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * 让测试类在开启限流的上下文里运行。本机（127.0.0.1）是可信代理，测试用 {@code X-Forwarded-For} 模拟不同的客户端 IP；
 * 部分限额调低，少发几次请求就能触发限流。同样标注本注解的测试类共用一个 Spring 上下文。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.trusted-proxies=127.0.0.1",
        "rate-limit.limits.register-per-hour=3",
        "rate-limit.limits.login-per-minute=3",
        "rate-limit.limits.search-per-minute=2",
        "rate-limit.limits.publish-per-hour=1",
        "rate-limit.limits.like-favorite-per-minute=2",
        "rate-limit.limits.follow-per-minute=2",
        "rate-limit.limits.comment-per-minute=5",
        "rate-limit.limits.comment-per-day=3",
})
public @interface RateLimitEnabled {
}
