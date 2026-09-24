package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 消费 redis-async 档的计数变更事件。去重在 Redis 里与累加一起原子执行，不用基于 MySQL 的
 * {@code @IdempotentConsumer}。
 */
@Component
@ConditionalOnProperty(name = "counter.mode", havingValue = "redis-async")
@RequiredArgsConstructor
class CounterChangedListener {

    static final String QUEUE = "counter.update";

    private final RedisCounterStore redisCounterStore;

    @Bean
    static Declarables counterUpdateQueue() {
        return EventQueues.declare(QUEUE, CounterChangedEvent.ROUTING_KEY);
    }

    @RabbitListener(queues = QUEUE)
    public void consume(CounterChangedEvent event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        redisCounterStore.increment(event.metric(), event.targetId(), event.delta(), messageId);
    }
}
