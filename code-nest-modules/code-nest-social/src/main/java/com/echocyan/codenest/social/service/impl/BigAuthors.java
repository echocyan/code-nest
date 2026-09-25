package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按粉丝数识别大 V：粉丝数不低于 {@code feed.big-author-threshold} 的作者。作者跨过阈值时不迁移已推送或未推送的文章，
 * 读 Feed 时按文章 ID 去重。
 */
@Component
class BigAuthors {

    private final CounterApi counterApi;
    private final long threshold;

    BigAuthors(CounterApi counterApi, @Value("${feed.big-author-threshold}") long threshold) {
        this.counterApi = counterApi;
        this.threshold = threshold;
    }

    /**
     * @return 给定作者中的大 V
     */
    Set<Long> among(Collection<Long> authorIds) {
        return counterApi.get(CounterTarget.USER, authorIds).entrySet().stream()
                .filter(entry -> entry.getValue().get(CounterMetric.USER_FOLLOWER) >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    boolean isBig(long authorId) {
        return !among(List.of(authorId)).isEmpty();
    }
}
