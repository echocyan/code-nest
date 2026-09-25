package com.echocyan.codenest.framework.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 按 {@code cache.mode} 创建 {@link TwoLevelCache}。每个缓存在所属模块里注册为一个 Bean：
 * <pre>{@code
 * @Bean
 * TwoLevelCache<UserBrief> userBriefCache(TwoLevelCaches caches) {
 *     return caches.create("user:brief", UserBrief.class);
 * }
 * }</pre>
 *
 * <p>two-level 档下订阅频道 {@value #INVALIDATE_CHANNEL}（见 {@link CacheInvalidationConfig}），
 * 收到某个 Redis key 的失效广播时清除本实例对应的本地缓存。
 */
@Component
public class TwoLevelCaches implements MessageListener {

    /**
     * 本地缓存失效广播的频道，消息内容是被删除的 Redis key。
     */
    static final String INVALIDATE_CHANNEL = "cache:invalidate";

    private final CacheMode mode;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    /**
     * 带本地缓存的实例，以 Redis key 前缀为 key。
     */
    private final Map<String, List<TwoLevelCache<?>>> localCaches = new ConcurrentHashMap<>();

    public TwoLevelCaches(@Value("${cache.mode}") CacheMode mode, StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.mode = mode;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 创建只用 Redis 的缓存，two-level 档下也不经过本地缓存。
     *
     * @param name 缓存名，格式为 {@code <模块>:<用途>}，同时是 Redis key 的前缀，全局唯一
     * @param type 缓存值的类型，以 JSON 存入 Redis
     */
    public <V> TwoLevelCache<V> create(String name, Class<V> type) {
        return new TwoLevelCache<>(name, type, mode, redis, jsonMapper, false, null);
    }

    /**
     * 创建两级缓存：two-level 档下先读本地缓存，本地未命中时先查布隆过滤器，再读 Redis；其他档与 {@link #create} 相同。
     *
     * @param name        同 {@link #create}
     * @param type        同 {@link #create}
     * @param bloomFilter 判定"一定不存在"的 ID 直接返回 null，不查 Redis 和数据库
     */
    public <V> TwoLevelCache<V> createTwoLevel(String name, Class<V> type, BloomFilter bloomFilter) {
        TwoLevelCache<V> cache = new TwoLevelCache<>(name, type, mode, redis, jsonMapper, true, bloomFilter);
        localCaches.computeIfAbsent(TwoLevelCache.keyPrefixOf(name), prefix -> new CopyOnWriteArrayList<>())
                .add(cache);
        return cache;
    }

    /**
     * @param name 过滤器名，Redis key 为 {@code bf:<name>}，全局唯一
     */
    public BloomFilter bloomFilter(String name) {
        return new BloomFilter(name, redis);
    }

    /**
     * 处理失效广播：按 key 前缀找到本地缓存并清除对应的 ID。
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        int separator = key.lastIndexOf(':');
        List<TwoLevelCache<?>> caches = localCaches.get(key.substring(0, separator + 1));
        if (caches != null) {
            long id = Long.parseLong(key.substring(separator + 1));
            caches.forEach(cache -> cache.invalidateLocal(id));
        }
    }
}
