package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.web.WebMvcConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code rate-limit.enabled} 为 true 时注册限流拦截器。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    /**
     * 排在 Sa-Token 拦截器（默认 order 0）之后。
     */
    private static final int ORDER = 1;

    private final SlidingWindowRateLimiter limiter;
    private final Environment environment;
    private final RateLimitProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiter, environment, properties))
                .addPathPatterns(WebMvcConfig.API_PREFIX + "/**")
                .order(ORDER);
    }
}
