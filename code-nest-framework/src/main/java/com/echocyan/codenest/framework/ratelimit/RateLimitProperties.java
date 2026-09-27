package com.echocyan.codenest.framework.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 限流配置。
 *
 * @param trustedProxies 可信代理的 IP，只有来自它们的请求才读取 {@code X-Forwarded-For}
 * @param rules          规则名到规则，接口上的 {@link RateLimit} 按名字引用；共用额度的接口引用同一个名字
 */
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(List<String> trustedProxies, Map<String, RuleProperties> rules) {

    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    /**
     * 一条规则。
     *
     * @param limit     窗口内最多允许的请求数
     * @param window    窗口长度，如 {@code 1m}、{@code 1h}、{@code 1d}
     * @param dimension 按谁计数
     */
    public record RuleProperties(Integer limit, Duration window, RateLimit.Dimension dimension) {
    }
}
