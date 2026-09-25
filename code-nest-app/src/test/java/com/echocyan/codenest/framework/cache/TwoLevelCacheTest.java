package com.echocyan.codenest.framework.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.echocyan.codenest.support.IntegrationTest;
import com.echocyan.codenest.support.TwoLevelCacheMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * two-level 档的本地缓存失效广播、同 key 合并加载与布隆过滤器。单个应用上下文无法模拟两个实例，
 * 这里用同名的两个 {@link TwoLevelCache} 代表两个实例，它们各有一份本地缓存，只经 Redis Pub/Sub 互相通知。
 */
@TwoLevelCacheMode
class TwoLevelCacheTest extends IntegrationTest {

    private static final long ID = 1;

    @Autowired
    private TwoLevelCaches caches;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void evictOnOneInstanceInvalidatesLocalCacheOfTheOther() {
        String name = uniqueName();
        BloomFilter bloomFilter = bloomFilterOf(name, ID);
        TwoLevelCache<String> first = caches.createTwoLevel(name, String.class, bloomFilter);
        TwoLevelCache<String> second = caches.createTwoLevel(name, String.class, bloomFilter);
        assertThat(first.get(ID, id -> "old")).isEqualTo("old");
        assertThat(second.get(ID, id -> "new")).isEqualTo("old");

        first.evict(ID);

        await().atMost(Duration.ofSeconds(5)).until(() -> "new".equals(second.get(ID, id -> "new")));
    }

    @Test
    void concurrentGetsOfSameKeyRunLoaderOnce() throws Exception {
        String name = uniqueName();
        TwoLevelCache<String> cache = caches.createTwoLevel(name, String.class, bloomFilterOf(name, ID));
        int threads = 8;
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return cache.get(ID, id -> {
                        loads.incrementAndGet();
                        sleep();
                        return "value";
                    });
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("value");
            }
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void bloomFilterRejectsIdsNeverAdded() {
        String name = uniqueName();
        BloomFilter bloomFilter = bloomFilterOf(name, ID);
        TwoLevelCache<String> cache = caches.createTwoLevel(name, String.class, bloomFilter);
        long created = 2;
        bloomFilter.add(created);

        assertThat(cache.get(ID, id -> "rebuilt")).isEqualTo("rebuilt");
        assertThat(cache.get(created, id -> "added")).isEqualTo("added");
        assertThat(cache.get(3, id -> "never added")).isNull();
    }

    @Test
    void lostBloomFilterIsRebuiltOnRestart() {
        String name = uniqueName();
        bloomFilterOf(name, ID);
        redis.delete("bf:" + name);

        // 重启：各实例启动时都会调用 rebuildIfAbsent
        BloomFilter restarted = bloomFilterOf(name, ID);
        TwoLevelCache<String> cache = caches.createTwoLevel(name, String.class, restarted);

        assertThat(cache.get(ID, id -> "existing")).isEqualTo("existing");
        assertThat(cache.get(2, id -> "missing")).isNull();
    }

    /**
     * 所有测试共用一个 Redis，缓存名需要全局唯一。
     */
    private static String uniqueName() {
        return "test:" + UUID.randomUUID();
    }

    /**
     * 建一个只含给定 ID 的布隆过滤器。
     */
    private BloomFilter bloomFilterOf(String name, Long... ids) {
        BloomFilter bloomFilter = caches.bloomFilter(name);
        bloomFilter.rebuildIfAbsent(afterId -> afterId == null ? List.of(ids) : List.of());
        return bloomFilter;
    }

    /** 让加载足够慢，其余线程在加载期间到达。 */
    private static void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
