package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计数先在 Redis 里原子累加，再异步批量落回 MySQL，热点对象不用排队等同一行的行锁。
 *
 * <p>精确计数经 {@link CounterChangedEvent} 随调用方的业务事务走 Outbox，由 {@link CounterChangedListener}
 * 去重后累加，所以读到的计数稍有延迟。一个事务里的多次变更在提交前合并成一个事件，只写一行 Outbox、发一条消息。
 * 浏览量是近似计数，在请求内直接累加，不走 MQ。
 */
@Service
@RequiredArgsConstructor
class RedisAsyncCounterService implements CounterApi {

    private final RedisCounterStore redisCounterStore;
    private final CounterTables counterTables;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public void increment(CounterMetric metric, long targetId, long delta) {
        if (metric == CounterMetric.ARTICLE_VIEW) {
            redisCounterStore.increment(metric, targetId, delta, null);
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Counter changes must be made in a transaction: " + metric);
        }
        pendingChanges().add(metric, targetId, delta);
    }

    /**
     * 当前事务的待发布变更。查找的是当前事务的同步回调而不是线程绑定的资源，
     * 内层的 REQUIRES_NEW 事务有自己的一份，随它自己提交。
     */
    private PendingChanges pendingChanges() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PendingChanges pending && pending.owner == this) {
                return pending;
            }
        }
        PendingChanges pending = new PendingChanges(this);
        TransactionSynchronizationManager.registerSynchronization(pending);
        return pending;
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

    /**
     * 一个事务里累积的计数变更，同一对象同一指标的变更先合并。提交前（beforeCommit）在事务内发布，
     * Outbox 记录与业务数据一起提交；事务回滚时不会调用 beforeCommit，变更随之丢弃。
     */
    private static final class PendingChanges implements TransactionSynchronization {

        private final RedisAsyncCounterService owner;
        private final Map<CounterKey, Long> deltas = new LinkedHashMap<>();

        private PendingChanges(RedisAsyncCounterService owner) {
            this.owner = owner;
        }

        void add(CounterMetric metric, long targetId, long delta) {
            deltas.merge(new CounterKey(metric, targetId), delta, Long::sum);
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            List<CounterChangedEvent.Change> changes = deltas.entrySet().stream()
                    .filter(entry -> entry.getValue() != 0)
                    .map(entry -> new CounterChangedEvent.Change(entry.getKey().metric(), entry.getKey().targetId(),
                            entry.getValue()))
                    .toList();
            deltas.clear();
            if (!changes.isEmpty()) {
                owner.domainEventPublisher.publish(new CounterChangedEvent(changes));
            }
        }
    }

    private record CounterKey(CounterMetric metric, long targetId) {
    }
}
