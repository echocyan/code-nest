package com.echocyan.codenest.framework.ratelimit;

import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.PropertyResolver;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 各接口方法上解析好占位符的 {@link RateLimit}。
 * <p>
 * 构造时校验：不同方法标注的同一个额度名必须是同一条规则（limit、window、dimension 都相同），否则共用的额度会按
 * 先到请求的规则计数；不一致时抛出异常，阻止启动。
 */
final class RateLimitRules {

    private final Map<Method, List<Rule>> rules = new HashMap<>();

    /**
     * @throws IllegalStateException 同一个额度名在不同方法上的规则不一致，消息里列出额度名与冲突的两个方法
     */
    RateLimitRules(Collection<Method> methods, PropertyResolver resolver) {
        // 额度名 → 最先见到它的方法与规则，用来比对之后的声明
        Map<String, Map.Entry<Method, Rule>> byKey = new HashMap<>();
        for (Method method : methods) {
            List<Rule> methodRules = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class)
                    .stream()
                    .map(rateLimit -> new Rule(rateLimit.key(),
                            Integer.parseInt(resolver.resolveRequiredPlaceholders(rateLimit.limit())),
                            DurationStyle.detectAndParse(resolver.resolveRequiredPlaceholders(rateLimit.window())),
                            rateLimit.dimension()))
                    .toList();
            for (Rule rule : methodRules) {
                Map.Entry<Method, Rule> first = byKey.putIfAbsent(rule.key(), Map.entry(method, rule));
                if (first != null && !first.getValue().equals(rule)) {
                    throw new IllegalStateException("Rate limit key '%s' is declared differently: %s on %s, %s on %s"
                            .formatted(rule.key(), first.getValue(), first.getKey().toGenericString(), rule,
                                    method.toGenericString()));
                }
            }
            if (!methodRules.isEmpty()) {
                rules.put(method, methodRules);
            }
        }
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
