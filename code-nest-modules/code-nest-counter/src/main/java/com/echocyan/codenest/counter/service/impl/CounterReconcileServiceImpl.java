package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.*;
import com.echocyan.codenest.counter.service.CounterReconcileService;
import com.echocyan.codenest.framework.lock.RedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 按指标逐页对账：取来源的一页精确计数，连同计数表里同一 ID 区间内不为 0、但这一页里没有的对象（精确计数为 0），
 * 与 {@link CounterApi#get} 读到的当前值、MySQL 计数表里的值分别比较，任一不一致就用 {@link CounterApi#reset} 修正。
 * redis-async 档下待落库标记丢失时，Redis 是对的而 MySQL 停在旧值，只比较前者会漏掉。
 *
 * <p>已知缺陷：统计与修正之间发生的并发写入，可能让修正后的计数与真实值相差 ±1，由下一次对账修正。
 * redis-async 档下还有两种情况：在途的计数事件在修正之后到达，会在修正值上再累加一次；
 * Redis 里不为 0、MySQL 里仍为 0 且关系表里没有对应行的对象，不在修正范围内。
 */
@Slf4j
@Service
class CounterReconcileServiceImpl implements CounterReconcileService {

    /**
     * 每页统计的对象数，也是一次批量读取当前计数的对象数上限。
     */
    static final int BATCH = 500;

    private static final String LOCK_KEY = "counter:reconcile:lock";

    /**
     * 锁的过期时间，要长于一次对账的耗时；实例崩溃时锁最迟在这之后释放。
     */
    private static final Duration LOCK_TTL = Duration.ofHours(1);

    private final CounterApi counterApi;
    private final CounterTables counterTables;
    private final RedisLock redisLock;
    private final Map<CounterMetric, CounterSource> sources = new EnumMap<>(CounterMetric.class);

    /**
     * @throws IllegalStateException 同一个指标有多个来源
     */
    CounterReconcileServiceImpl(CounterApi counterApi, CounterTables counterTables, RedisLock redisLock,
                                List<CounterSource> sources) {
        this.counterApi = counterApi;
        this.counterTables = counterTables;
        this.redisLock = redisLock;
        sources.forEach(source -> source.metrics().forEach(metric -> {
            if (this.sources.putIfAbsent(metric, source) != null) {
                throw new IllegalStateException("计数指标有多个对账来源: " + metric);
            }
        }));
    }

    @Override
    public Optional<Map<CounterMetric, Long>> reconcile() {
        Optional<Map<CounterMetric, Long>> result = redisLock.tryRun(LOCK_KEY, LOCK_TTL, () -> {
            Map<CounterMetric, Long> corrected = new EnumMap<>(CounterMetric.class);
            sources.forEach((metric, source) -> corrected.put(metric, reconcile(metric, source)));
            log.info("Counter reconciliation finished, corrected: {}", corrected);
            return corrected;
        });
        if (result.isEmpty()) {
            log.info("Counter reconciliation is already running on another instance, skipped");
        }
        return result;
    }

    /**
     * 每周一凌晨 4 点执行一次。
     */
    @Scheduled(cron = "0 0 4 * * MON")
    void scheduledReconcile() {
        reconcile();
    }

    /**
     * @return 被修正的对象数
     */
    private long reconcile(CounterMetric metric, CounterSource source) {
        long corrected = 0;
        long afterId = 0;
        boolean last;
        do {
            List<IdCount> page = source.countAfter(metric, afterId, BATCH);
            last = page.size() < BATCH;
            long upperId = last ? Long.MAX_VALUE : page.getLast().id();
            Map<Long, Long> exact = new HashMap<>();
            // 计数表里有值、但关系表里已经没有对应行的对象，精确计数为 0
            counterTables.selectNonZeroIds(metric, afterId, upperId).forEach(id -> exact.put(id, 0L));
            page.forEach(row -> exact.put(row.id(), row.count()));
            corrected += correct(metric, exact);
            afterId = upperId;
        } while (!last);
        return corrected;
    }

    /**
     * 把与精确计数不一致的当前值改为精确计数。
     *
     * @param exact 对象 ID 到精确计数
     * @return 被修正的对象数
     */
    private long correct(CounterMetric metric, Map<Long, Long> exact) {
        long corrected = 0;
        List<Long> ids = List.copyOf(exact.keySet());
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            Map<Long, Counts> current = counterApi.get(metric.target(), batch);
            Map<Long, Counts> stored = counterTables.select(metric.target(), batch);
            for (Long id : batch) {
                long value = exact.get(id);
                long storedValue = stored.containsKey(id) ? stored.get(id).get(metric) : 0;
                if (current.get(id).get(metric) != value || storedValue != value) {
                    counterApi.reset(metric, id, value);
                    corrected++;
                }
            }
        }
        return corrected;
    }
}
