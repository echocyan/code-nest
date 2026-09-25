package com.echocyan.codenest.framework.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 按 {@code cache.mode} 创建 {@link TwoLevelCache}。每个缓存在所属模块里注册为一个 Bean：
 * <pre>{@code
 * @Bean
 * TwoLevelCache<UserBrief> userBriefCache(TwoLevelCaches caches) {
 *     return caches.create("user:brief", UserBrief.class);
 * }
 * }</pre>
 */
@Component
public class TwoLevelCaches {

    private final CacheMode mode;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public TwoLevelCaches(@Value("${cache.mode}") CacheMode mode, StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.mode = mode;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param name 缓存名，格式为 {@code <模块>:<用途>}，同时是 Redis key 的前缀，全局唯一
     * @param type 缓存值的类型，以 JSON 存入 Redis
     */
    public <V> TwoLevelCache<V> create(String name, Class<V> type) {
        return new TwoLevelCache<>(name, type, mode, redis, jsonMapper);
    }
}
