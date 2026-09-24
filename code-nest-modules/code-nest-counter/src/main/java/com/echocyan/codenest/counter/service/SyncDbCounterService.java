package com.echocyan.codenest.counter.service;

import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.mapper.CounterMapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基线实现：在调用方的事务里直接更新计数行，读取直接查 MySQL。热点对象的并发更新会排队等同一行的行锁。
 *
 * <p>表名、列名按命名约定从枚举推导：{@code ARTICLE} → {@code article_stat.article_id}，
 * {@code ARTICLE_LIKE} → {@code like_count}。
 */
@Service
@ConditionalOnProperty(name = "counter.mode", havingValue = "sync-db")
@RequiredArgsConstructor
class SyncDbCounterService implements CounterApi {

    private final CounterMapper counterMapper;

    @Override
    public void increment(CounterMetric metric, long targetId, long delta) {
        CounterTarget target = metric.target();
        counterMapper.increment(table(target), idColumn(target), column(metric), targetId, delta, DateTimes.now());
    }

    @Override
    public Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        Map<Long, Counts> result = new HashMap<>();
        targetIds.forEach(id -> result.put(id, new Counts(Map.of())));
        if (targetIds.isEmpty()) {
            return result;
        }
        List<CounterMetric> metrics = Arrays.stream(CounterMetric.values()).filter(m -> m.target() == target).toList();
        for (Map<String, Object> row : counterMapper.selectByIds(table(target), idColumn(target), targetIds)) {
            Map<CounterMetric, Long> values = new EnumMap<>(CounterMetric.class);
            metrics.forEach(metric -> values.put(metric, ((Number) row.get(column(metric))).longValue()));
            result.put(((Number) row.get(idColumn(target))).longValue(), new Counts(values));
        }
        return result;
    }

    @Override
    public void reset(CounterMetric metric, long targetId, long value) {
        CounterTarget target = metric.target();
        counterMapper.reset(table(target), idColumn(target), column(metric), targetId, value, DateTimes.now());
    }

    private static String table(CounterTarget target) {
        return lower(target.name()) + "_stat";
    }

    private static String idColumn(CounterTarget target) {
        return lower(target.name()) + "_id";
    }

    private static String column(CounterMetric metric) {
        return lower(metric.name().substring(metric.target().name().length() + 1)) + "_count";
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
