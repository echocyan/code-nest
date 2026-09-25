package com.echocyan.codenest.framework.ratelimit;

import java.lang.annotation.*;

/**
 * 对 Controller 方法按滑动窗口限流，超限时返回 429 和 {@code Retry-After}。
 * <p>
 * 可以重复标注，同时限制短期和长期频率；一次请求要所有额度都有余量才放行，被拒绝的请求不占用任何额度。
 * 不同方法标注相同的 {@link #key} 和维度时共用额度。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 额度名，同一个方法上的各条额度不能重名。
     */
    String key();

    /**
     * 窗口内最多允许的请求数，支持 {@code ${...}} 占位符。
     */
    String limit();

    /**
     * 窗口长度，如 {@code 1m}、{@code 1h}、{@code 1d}，支持 {@code ${...}} 占位符。
     */
    String window();

    Dimension dimension();

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
