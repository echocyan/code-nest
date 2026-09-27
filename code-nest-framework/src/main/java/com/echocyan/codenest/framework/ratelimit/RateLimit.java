package com.echocyan.codenest.framework.ratelimit;

import java.lang.annotation.*;

/**
 * 对 Controller 方法按滑动窗口限流，超限时返回 429 和 {@code Retry-After}。
 * <p>
 * 规则（限额、窗口、维度）在配置 {@code rate-limit.rules.<规则名>} 下，注解只写规则名；不同方法写同一个规则名时共用额度。
 * 可以重复标注，同时限制短期和长期频率；一次请求要所有额度都有余量才放行，被拒绝的请求不占用任何额度。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 规则名，必须在 {@code rate-limit.rules} 下配置。
     */
    String value();

    /**
     * 按谁计数。
     */
    enum Dimension {

        /**
         * 当前登录用户，只能用于需要登录的接口。
         */
        USER,

        /**
         * 客户端 IP，只在请求来自可信代理时才读取 {@code X-Forwarded-For}。
         */
        IP
    }
}
