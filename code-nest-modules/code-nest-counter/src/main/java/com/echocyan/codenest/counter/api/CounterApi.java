package com.echocyan.codenest.counter.api;

import java.util.Collection;
import java.util.Map;

/**
 * counter 模块对其他模块的门面，由 {@code counter.mode} 选择实现。计数最小为 0。
 */
public interface CounterApi {

    /**
     * 增减一项计数。在调用方的事务中调用，随业务一起提交或回滚。
     *
     * @param delta 可为负数；结果低于 0 时按 0 处理
     */
    void increment(CounterMetric metric, long targetId, long delta);

    /**
     * 批量读取同一类对象的全部计数。
     *
     * @return 每个传入的 ID 都有一项；没有计数的对象各项均为 0
     */
    Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds);

    /**
     * 把一项计数直接改为给定值，供对账修正。
     *
     * @throws IllegalArgumentException value 为负数
     */
    void reset(CounterMetric metric, long targetId, long value);
}
