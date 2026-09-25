package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.mapper.CounterMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 三张计数表的读写，两种实现共用。
 *
 * <p>表名、列名按命名约定从枚举推导：{@code ARTICLE} → {@code article_stat.article_id}，
 * {@code ARTICLE_LIKE} → 字段 {@code like}、列 {@code like_count}。
 */
@Component
@RequiredArgsConstructor
class CounterTables {

    private final CounterMapper counterMapper;

    /**
     * 计数行不存在时插入，存在时累加；结果不低于 0。
     */
    void increment(CounterMetric metric, long targetId, long delta) {
        CounterTarget target = metric.target();
        counterMapper.increment(table(target), idColumn(target), column(metric), targetId, delta, DateTimes.now());
    }

    void reset(CounterMetric metric, long targetId, long value) {
        CounterTarget target = metric.target();
        counterMapper.reset(table(target), idColumn(target), column(metric), targetId, value, DateTimes.now());
    }

    /**
     * @return 只包含有计数行的对象
     */
    Map<Long, Counts> select(CounterTarget target, Collection<Long> targetIds) {
        Map<Long, Counts> result = new HashMap<>();
        if (targetIds.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : counterMapper.selectByIds(table(target), idColumn(target), targetIds)) {
            Map<CounterMetric, Long> values = new EnumMap<>(CounterMetric.class);
            metrics(target).forEach(metric -> values.put(metric, ((Number) row.get(column(metric))).longValue()));
            result.put(((Number) row.get(idColumn(target))).longValue(), new Counts(values));
        }
        return result;
    }

    /**
     * @return ID 在 (afterId, upperId] 内、MySQL 里该项计数不为 0 的对象
     */
    List<Long> selectNonZeroIds(CounterMetric metric, long afterId, long upperId) {
        CounterTarget target = metric.target();
        return counterMapper.selectNonZeroIds(table(target), idColumn(target), column(metric), afterId, upperId);
    }

    /**
     * 把各对象的全部计数按绝对值写入，没有计数行时插入。
     */
    void upsert(CounterTarget target, Map<Long, Counts> counts) {
        if (counts.isEmpty()) {
            return;
        }
        List<CounterMetric> metrics = metrics(target);
        List<List<Long>> rows = new ArrayList<>();
        counts.forEach((id, values) -> {
            List<Long> row = new ArrayList<>();
            row.add(id);
            metrics.forEach(metric -> row.add(values.get(metric)));
            rows.add(row);
        });
        counterMapper.upsertAll(table(target), idColumn(target), metrics.stream().map(CounterTables::column).toList(),
                rows, DateTimes.now());
    }

    /**
     * @return 属于该对象类型的全部指标，按枚举声明顺序
     */
    static List<CounterMetric> metrics(CounterTarget target) {
        return Arrays.stream(CounterMetric.values()).filter(metric -> metric.target() == target).toList();
    }

    /**
     * 去掉对象类型前缀后的指标名，例如 {@code USER_LIKE_RECEIVED} → {@code like_received}。
     */
    static String field(CounterMetric metric) {
        return lower(metric.name().substring(metric.target().name().length() + 1));
    }

    static String lower(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String table(CounterTarget target) {
        return lower(target.name()) + "_stat";
    }

    private static String idColumn(CounterTarget target) {
        return lower(target.name()) + "_id";
    }

    private static String column(CounterMetric metric) {
        return field(metric) + "_count";
    }
}
