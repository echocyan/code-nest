package com.echocyan.codenest.counter.api;

import java.util.List;
import java.util.Set;

/**
 * 精确计数的真实数据来源，由掌握关系表或内容表的模块实现并注册为 Bean，供计数对账重新统计。
 * 每个指标只能由一个来源负责；浏览量是近似计数，没有来源。
 */
public interface CounterSource {

    /**
     * @return 本来源负责的指标
     */
    Set<CounterMetric> metrics();

    /**
     * 用 {@code GROUP BY} 重新统计一页精确计数。没有出现在任何一页里的对象，精确计数视为 0。
     *
     * @param metric  {@link #metrics()} 中的一项
     * @param afterId 只统计 ID 大于它的对象
     * @return 计数大于 0 的对象，按 ID 升序，至多 limit 个
     */
    List<IdCount> countAfter(CounterMetric metric, long afterId, int limit);
}
