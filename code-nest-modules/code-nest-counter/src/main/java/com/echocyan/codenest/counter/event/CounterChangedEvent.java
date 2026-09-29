package com.echocyan.codenest.counter.event;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.framework.mq.DomainEvent;

import java.util.List;

/**
 * 计数变更事件，包含一个业务事务里的全部计数变更（如点赞同时改文章点赞数与作者获赞数）。
 * 随调用方的业务事务经 Outbox 投递，只由 counter 模块自己消费。
 */
@DomainEvent("counter.changed")
public record CounterChangedEvent(List<Change> changes) {

    public record Change(CounterMetric metric, long targetId, long delta) {
    }
}
