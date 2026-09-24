package com.echocyan.codenest.counter.api;

import java.util.Map;

/**
 * 一个对象的各项计数。
 */
public record Counts(Map<CounterMetric, Long> values) {

    /**
     * @return 没有记录的指标为 0
     */
    public long get(CounterMetric metric) {
        return values.getOrDefault(metric, 0L);
    }
}
