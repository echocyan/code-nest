package com.echocyan.codenest.counter.event;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * redis-async 档的计数变更事件。随调用方的业务事务经 Outbox 投递，只由 counter 模块自己消费。
 */
@DomainEvent(CounterChangedEvent.ROUTING_KEY)
public record CounterChangedEvent(CounterMetric metric, long targetId, long delta) {

    public static final String ROUTING_KEY = "counter.changed";
}
