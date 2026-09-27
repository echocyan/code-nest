package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.web.WebMvcConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@code rate-limit.enabled} 为 true 时注册限流拦截器。注册时解析全部 Controller 方法上的 {@link RateLimit}
 * 并校验同名额度的规则一致，不一致时启动失败，见 {@link RateLimitRules}。
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
    private final ListableBeanFactory beanFactory;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimitRules rules = new RateLimitRules(controllerMethods(), environment);
        registry.addInterceptor(new RateLimitInterceptor(limiter, rules, properties))
                .addPathPatterns(WebMvcConfig.API_PREFIX + "/**")
                .order(ORDER);
    }

    /**
     * 全部 Controller 声明的方法，只看 Bean 的类型，不创建 Bean。
     */
    private List<Method> controllerMethods() {
        return Stream.of(beanFactory.getBeanNamesForAnnotation(Controller.class))
                .map(beanFactory::getType)
                .filter(Objects::nonNull)
                .map(ClassUtils::getUserClass)
                .flatMap(type -> Stream.of(ReflectionUtils.getUniqueDeclaredMethods(type)))
                .toList();
    }
}
