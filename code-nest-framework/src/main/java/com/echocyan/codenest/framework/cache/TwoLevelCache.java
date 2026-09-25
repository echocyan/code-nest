package com.echocyan.codenest.framework.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 以 ID 为 key 的读缓存，采用 Cache-Aside：读取未命中时执行加载函数并回填，数据库更新后由写方调用 {@link #evict}。
 * 由 {@link TwoLevelCaches} 按 {@code cache.mode} 创建，{@code none} 档下每次都执行加载函数，{@link #evict} 什么也不做。
 *
 * <p>Redis 中的 key 为 {@code cache:<name>:<id>}，值是 JSON，TTL 为 30 分钟加 0–5 分钟的随机抖动，
 * 避免同一批写入的 key 同时过期。加载结果为空时缓存一个 JSON {@code null}，TTL 60 秒，
 * 反复查询不存在的 ID 不会反复打到数据库。
 *
 * <p><b>隐式行为：</b>{@link #evict} 在有活跃事务时推迟到事务提交后（afterCommit）执行，事务回滚则不删除；
 * 此时删除失败只记日志，不影响已提交的业务。没有事务时立即删除，失败直接抛出。
 *
 * <p><b>残留的竞态：</b>读请求未命中、从数据库读到旧值后停顿；这期间写请求更新数据库并删除缓存；
 * 读请求随后把旧值回填。旧值最多保留到 TTL 过期。窗口需要"读库比写库加删缓存还慢"，实际极小；
 * 写方经 MQ 再删一次（延迟双删）可以覆盖其中大部分情况。
 *
 * @param <V> 缓存值的类型
 */
@Slf4j
public class TwoLevelCache<V> {

    private static final Duration TTL = Duration.ofMinutes(30);

    private static final long TTL_JITTER_SECONDS = Duration.ofMinutes(5).toSeconds();

    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 空值标记，即 JSON {@code null}；正常的缓存值是 JSON 对象，不会与它相同。 */
    private static final String NULL = "null";

    private final String keyPrefix;
    private final Class<V> type;
    private final CacheMode mode;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    TwoLevelCache(String name, Class<V> type, CacheMode mode, StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.keyPrefix = "cache:" + name + ":";
        this.type = type;
        this.mode = mode;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 读取一个值，未命中时执行 loader 并回填。
     *
     * @param loader 从数据库加载，不存在时返回 null
     * @return 不存在时为 null
     */
    public V get(long id, Function<Long, V> loader) {
        if (mode == CacheMode.NONE) {
            return loader.apply(id);
        }
        String key = key(id);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return decode(cached);
        }
        V value = loader.apply(id);
        String encoded = encode(value);
        redis.opsForValue().set(key, encoded, ttlOf(encoded));
        return value;
    }

    /**
     * 批量读取，一次 {@code MGET}；未命中的 ID 合在一起执行一次 batchLoader，再用 pipeline 回填。
     *
     * @param batchLoader 从数据库批量加载，不存在的 ID 不出现在结果中；只会收到非空的 ID 集合
     * @return 以 ID 为 key；不存在的 ID 不出现在结果中
     */
    public Map<Long, V> getAll(Collection<Long> ids, Function<Collection<Long>, Map<Long, V>> batchLoader) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (mode == CacheMode.NONE) {
            return batchLoader.apply(ids);
        }
        List<Long> distinct = ids.stream().distinct().toList();
        List<String> cached = redis.opsForValue().multiGet(distinct.stream().map(this::key).toList());
        Map<Long, V> result = new HashMap<>();
        List<Long> misses = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i++) {
            String hit = cached.get(i);
            if (hit == null) {
                misses.add(distinct.get(i));
            } else if (!NULL.equals(hit)) {
                result.put(distinct.get(i), decode(hit));
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        Map<Long, V> loaded = batchLoader.apply(misses);
        result.putAll(loaded);
        Map<String, String> backfill = new LinkedHashMap<>();
        misses.forEach(id -> backfill.put(key(id), encode(loaded.get(id))));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            backfill.forEach((key, value) -> connection.stringCommands().set(
                    key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8),
                    Expiration.from(ttlOf(value)), SetOption.upsert()));
            return null;
        });
        return result;
    }

    /**
     * 删除缓存。有活跃事务时推迟到提交后执行，见类注释。
     */
    public void evict(long id) {
        if (mode == CacheMode.NONE) {
            return;
        }
        String key = key(id);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        redis.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Failed to evict cache after commit: key={}", key, e);
                    }
                }
            });
        } else {
            redis.delete(key);
        }
    }

    private String key(long id) {
        return keyPrefix + id;
    }

    private String encode(V value) {
        return value == null ? NULL : jsonMapper.writeValueAsString(value);
    }

    private V decode(String json) {
        return NULL.equals(json) ? null : jsonMapper.readValue(json, type);
    }

    private static Duration ttlOf(String encoded) {
        if (NULL.equals(encoded)) {
            return NULL_TTL;
        }
        return TTL.plusSeconds(ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1));
    }
}
