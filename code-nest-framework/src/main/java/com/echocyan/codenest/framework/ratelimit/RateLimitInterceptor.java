package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.SlidingWindowRateLimiter.Quota;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行 {@link RateLimit}。注册在 Sa-Token 拦截器之后，登录校验通过后才计数，按用户限流时能拿到用户 ID。
 * Redis 不可用时放行请求并打告警日志，限流故障不拖垮主业务。
 */
@Slf4j
class RateLimitInterceptor implements HandlerInterceptor {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final SlidingWindowRateLimiter limiter;
    private final Environment environment;
    private final Set<String> trustedProxies;

    /**
     * 各方法解析好占位符的额度，方法上没有 {@link RateLimit} 时为空列表。
     */
    private final Map<Method, List<Rule>> rules = new ConcurrentHashMap<>();

    RateLimitInterceptor(SlidingWindowRateLimiter limiter, Environment environment, RateLimitProperties properties) {
        this.limiter = limiter;
        this.environment = environment;
        this.trustedProxies = Set.copyOf(properties.trustedProxies());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        List<Rule> methodRules = rules.computeIfAbsent(handlerMethod.getMethod(), this::resolveRules);
        if (methodRules.isEmpty()) {
            return true;
        }
        List<Quota> quotas = methodRules.stream()
                .map(rule -> new Quota(rule.key() + ":" + subject(rule.dimension(), request), rule.limit(),
                        rule.window()))
                .toList();
        long waitMillis;
        try {
            waitMillis = limiter.tryAcquire(quotas);
        } catch (DataAccessException e) {
            log.warn("限流不可用，放行请求: {} {}", request.getMethod(), request.getRequestURI(), e);
            return true;
        }
        if (waitMillis > 0) {
            throw new RateLimitedException(Math.ceilDiv(waitMillis, 1000));
        }
        return true;
    }

    private List<Rule> resolveRules(Method method) {
        return AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class).stream()
                .map(rateLimit -> new Rule(rateLimit.key(),
                        Integer.parseInt(environment.resolveRequiredPlaceholders(rateLimit.limit())),
                        DurationStyle.detectAndParse(environment.resolveRequiredPlaceholders(rateLimit.window())),
                        rateLimit.dimension()))
                .toList();
    }

    private String subject(RateLimit.Dimension dimension, HttpServletRequest request) {
        return switch (dimension) {
            case USER -> "user:" + AuthContext.currentUserId();
            case IP -> "ip:" + clientIp(request);
        };
    }

    /**
     * 请求不来自可信代理时直接取对端地址。来自可信代理时，从右往左取 {@code X-Forwarded-For} 中第一个不是可信代理的地址：
     * 代理只会在右侧追加它看到的对端地址，左侧的内容可能是客户端伪造的。
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    private record Rule(String key, int limit, Duration window, RateLimit.Dimension dimension) {
    }
}
