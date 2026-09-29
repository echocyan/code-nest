package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消费计数变更事件。去重在 Redis 里与累加一起原子执行，不用基于 MySQL 的
 * {@code @IdempotentConsumer}。
 */
@Component
@RequiredArgsConstructor
class CounterChangedListener {

    static final String QUEUE = "counter.update";

    private final RedisCounterStore redisCounterStore;

    @Bean
    static Declarables counterUpdateQueue() {
        return EventQueues.declare(QUEUE, CounterChangedListener.class);
    }

    /**
     * 各项变更按 messageId 加序号分别去重：中途失败重试时，已生效的变更不会重复累加。
     */
    @RabbitListener(queues = QUEUE)
    public void consume(CounterChangedEvent event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        List<CounterChangedEvent.Change> changes = event.changes();
        for (int i = 0; i < changes.size(); i++) {
            CounterChangedEvent.Change change = changes.get(i);
            redisCounterStore.increment(change.metric(), change.targetId(), change.delta(), messageId + ":" + i);
        }
    }
}
