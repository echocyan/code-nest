package com.echocyan.codenest.framework.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 以 ID 为 key 的读缓存，采用 Cache-Aside：读取未命中时执行加载函数并回填，数据库更新后由写方调用 {@link #evict}。
 * 由 {@link TwoLevelCaches} 按 {@code cache.mode} 创建，{@code none} 档下每次都执行加载函数，{@link #evict} 什么也不做。
 *
 * <p>Redis 中的 key 为 {@code cache:<name>:<id>}，值是 JSON，TTL 为 30 分钟加 0–5 分钟的随机抖动，
 * 避免同一批写入的 key 同时过期。加载结果为空时缓存一个 JSON {@code null}，TTL 60 秒，
 * 反复查询不存在的 ID 不会反复打到数据库。
 *
 * <p><b>两级缓存：</b>经 {@link TwoLevelCaches#createTwoLevel} 创建的缓存在 two-level 档下多一级 Caffeine 本地缓存，
 * 最多 10000 条，写入 60 秒后过期。{@link #get} 的读取顺序是本地缓存 → 布隆过滤器 → Redis → 加载函数；
 * 布隆过滤器判定一定不存在的 ID 直接返回 null。本地缓存未命中时，同一实例上对同一个 ID 的并发请求只加载一次，
 * 防止热点 key 过期时击穿到数据库。{@link #getAll} 同样先读本地缓存，但不经过布隆过滤器。
 * 空值不放进本地缓存。本地缓存返回的是同一个对象，调用方不能修改它。
 *
 * <p><b>本地缓存失效：</b>{@link #evict} 删除 Redis 后清除本实例的本地缓存，再经 Redis Pub/Sub 频道
 * {@code cache:invalidate} 广播，其他实例收到后清除各自的本地缓存。Pub/Sub 不保证送达，漏收的实例最多读到
 * 60 秒的旧值，由本地缓存的过期时间兜底。
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

    private static final int LOCAL_MAXIMUM_SIZE = 10_000;

    private static final Duration LOCAL_TTL = Duration.ofSeconds(60);

    /**
     * 空值标记，即 JSON {@code null}；正常的缓存值是 JSON 对象，不会与它相同。
     */
    private static final String NULL = "null";

    private final String keyPrefix;
    private final Class<V> type;
    private final CacheMode mode;
    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    /**
     * 本地缓存，只在 two-level 档的两级缓存中存在，否则为 null。
     */
    private final Cache<Long, V> local;

    /**
     * 只在有本地缓存时使用。
     */
    private final BloomFilter bloomFilter;

    TwoLevelCache(String name, Class<V> type, CacheMode mode, StringRedisTemplate redis, JsonMapper jsonMapper,
                  boolean twoLevel, BloomFilter bloomFilter) {
        this.keyPrefix = keyPrefixOf(name);
        this.type = type;
        this.mode = mode;
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.local = twoLevel && mode == CacheMode.TWO_LEVEL
                ? Caffeine.newBuilder().maximumSize(LOCAL_MAXIMUM_SIZE).expireAfterWrite(LOCAL_TTL).build()
                : null;
        this.bloomFilter = bloomFilter;
    }

    static String keyPrefixOf(String name) {
        return "cache:" + name + ":";
    }

    private static Duration ttlOf(String encoded) {
        if (NULL.equals(encoded)) {
            return NULL_TTL;
        }
        return TTL.plusSeconds(ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS + 1));
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
        if (local == null) {
            return getFromRedis(id, loader);
        }
        return local.get(id, key -> bloomFilter.mightContain(key) ? getFromRedis(key, loader) : null);
    }

    private V getFromRedis(long id, Function<Long, V> loader) {
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
        if (local == null) {
            return getAllFromRedis(ids, batchLoader);
        }
        return local.getAll(ids, misses -> getAllFromRedis(List.copyOf(misses), batchLoader));
    }

    private Map<Long, V> getAllFromRedis(Collection<Long> ids,
                                         Function<Collection<Long>, Map<Long, V>> batchLoader) {
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
     * 删除缓存，有本地缓存时同时广播失效。有活跃事务时推迟到提交后执行，见类注释。
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
                        evictNow(id, key);
                    } catch (RuntimeException e) {
                        log.warn("Failed to evict cache after commit: key={}", key, e);
                    }
                }
            });
        } else {
            evictNow(id, key);
        }
    }

    /**
     * 先删 Redis 再清本地缓存，避免本实例从 Redis 读回旧值；最后广播，让其他实例清除各自的本地缓存。
     */
    private void evictNow(long id, String key) {
        redis.delete(key);
        if (local != null) {
            local.invalidate(id);
            redis.convertAndSend(TwoLevelCaches.INVALIDATE_CHANNEL, key);
        }
    }

    /**
     * 收到失效广播时清除本地缓存。
     */
    void invalidateLocal(long id) {
        if (local != null) {
            local.invalidate(id);
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
}
