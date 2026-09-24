package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基线实现：在调用方的事务里直接更新计数行，读取直接查 MySQL。热点对象的并发更新会排队等同一行的行锁。
 */
@Service
@ConditionalOnProperty(name = "counter.mode", havingValue = "sync-db")
@RequiredArgsConstructor
class SyncDbCounterService implements CounterApi {

    private final CounterTables counterTables;

    @Override
    public void increment(CounterMetric metric, long targetId, long delta) {
        counterTables.increment(metric, targetId, delta);
    }

    @Override
    public Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        Map<Long, Counts> result = new HashMap<>();
        targetIds.forEach(id -> result.put(id, new Counts(Map.of())));
        result.putAll(counterTables.select(target, targetIds));
        return result;
    }

    @Override
    public void reset(CounterMetric metric, long targetId, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("计数不能为负数: " + metric + "=" + value);
        }
        counterTables.reset(metric, targetId, value);
    }
}
