package com.echocyan.codenest.counter.service;

import com.echocyan.codenest.counter.api.CounterMetric;

import java.util.Map;
import java.util.Optional;

/**
 * 计数对账：按各 {@link com.echocyan.codenest.counter.api.CounterSource} 重新统计出的精确计数，修正 Redis 和 MySQL
 * 里的计数。浏览量不参与对账。
 */
public interface CounterReconcileService {

    /**
     * 同步执行一次对账。多个实例之间互斥，同一时刻只有一个实例在对账。
     *
     * @return 各指标被修正的对象数；其他实例正在对账时为空
     */
    Optional<Map<CounterMetric, Long>> reconcile();
}
