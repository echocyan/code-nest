package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 优化实现：计数先在 Redis 里原子累加，再异步批量落回 MySQL，热点对象不再排队等同一行的行锁。
 *
 * <p>精确计数经 {@link CounterChangedEvent} 随调用方的业务事务走 Outbox，由 {@link CounterChangedListener}
 * 去重后累加，所以读到的计数稍有延迟。浏览量是近似计数，在请求内直接累加，不走 MQ。
 */
@Service
@ConditionalOnProperty(name = "counter.mode", havingValue = "redis-async")
@RequiredArgsConstructor
class RedisAsyncCounterService implements CounterApi {

    private final RedisCounterStore redisCounterStore;
    private final CounterTables counterTables;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public void increment(CounterMetric metric, long targetId, long delta) {
        if (metric == CounterMetric.ARTICLE_VIEW) {
            redisCounterStore.increment(metric, targetId, delta, null);
        } else {
            domainEventPublisher.publish(new CounterChangedEvent(metric, targetId, delta));
        }
    }

    @Override
    public Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        return redisCounterStore.get(target, targetIds);
    }

    /**
     * 先改 Redis 再改 MySQL：反过来的话，两步之间落库会把 Redis 里的旧值写回 MySQL。与并发写入交错时可能有 ±1 的误差，
     * 由下一次对账修正。
     */
    @Override
    public void reset(CounterMetric metric, long targetId, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("计数不能为负数: " + metric + "=" + value);
        }
        redisCounterStore.resetIfPresent(metric, targetId, value);
        counterTables.reset(metric, targetId, value);
    }
}
