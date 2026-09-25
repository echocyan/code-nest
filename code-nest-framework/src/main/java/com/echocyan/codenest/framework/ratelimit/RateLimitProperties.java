package com.echocyan.codenest.framework.ratelimit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流配置；各接口的限额在 {@code rate-limit.limits} 下，由 {@link RateLimit} 以占位符引用。
 *
 * @param trustedProxies 可信代理的 IP，只有来自它们的请求才读取 {@code X-Forwarded-For}
 */
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(List<String> trustedProxies) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
