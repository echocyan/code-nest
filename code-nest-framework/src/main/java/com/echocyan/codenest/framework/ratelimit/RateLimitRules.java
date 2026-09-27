package com.echocyan.codenest.framework.ratelimit;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.framework.ratelimit.RateLimitProperties.RuleProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 各接口方法上 {@link RateLimit} 引用的规则，取自配置。
 * <p>
 * 构造时校验，不满足就抛出异常、阻止启动：引用的规则名必须已配置且限额、窗口、维度齐全；按用户计数的规则不能用在
 * 标了 {@link SaIgnore} 的匿名接口上，否则请求会因为取不到当前用户而返回 401。
 */
final class RateLimitRules {

    private final Map<Method, List<Rule>> rules = new HashMap<>();

    /**
     * @param configured 规则名到规则，即 {@code rate-limit.rules}
     * @throws IllegalStateException 规则名未配置或配置不全、匿名接口按用户限流，消息里列出规则名与方法
     */
    RateLimitRules(Collection<Method> methods, Map<String, RuleProperties> configured) {
        for (Method method : methods) {
            List<Rule> methodRules = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class)
                    .stream()
                    .map(rateLimit -> ruleOf(rateLimit.value(), configured.get(rateLimit.value()), method))
                    .toList();
            if (!methodRules.isEmpty()) {
                rules.put(method, methodRules);
            }
        }
    }

    private static Rule ruleOf(String key, RuleProperties properties, Method method) {
        if (properties == null || properties.limit() == null || properties.window() == null
                || properties.dimension() == null) {
            throw new IllegalStateException("Rate limit rule '%s' used on %s is not fully configured under rate-limit.rules"
                    .formatted(key, method.toGenericString()));
        }
        if (properties.dimension() == RateLimit.Dimension.USER && isAnonymous(method)) {
            throw new IllegalStateException("Rate limit rule '%s' counts per user but %s allows anonymous access"
                    .formatted(key, method.toGenericString()));
        }
        return new Rule(key, properties.limit(), properties.window(), properties.dimension());
    }

    private static boolean isAnonymous(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, SaIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), SaIgnore.class);
    }

    /**
     * @return 方法上的额度；没有标注 {@link RateLimit} 或不在构造时给出的方法中时为空列表
     */
    List<Rule> of(Method method) {
        return rules.getOrDefault(method, List.of());
    }

    record Rule(String key, int limit, Duration window, RateLimit.Dimension dimension) {
    }
}
