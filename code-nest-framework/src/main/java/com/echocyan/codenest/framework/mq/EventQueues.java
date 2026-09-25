package com.echocyan.codenest.framework.mq;

import org.springframework.amqp.core.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息拓扑：所有事件发往 topic 交换机 {@link #EXCHANGE}，重试耗尽的消息经 {@link #DEAD_LETTER_EXCHANGE}
 * 进入各消费队列对应的 {@code <queue>.dlq}。交换机、队列都持久化，队列用 classic 类型。
 */
public final class EventQueues {

    public static final String EXCHANGE = "codenest.events";

    public static final String DEAD_LETTER_EXCHANGE = "codenest.dlx";

    private EventQueues() {
    }

    /**
     * 声明一个消费队列及其死信队列，并把消费队列按路由键绑定到 {@link #EXCHANGE}。在消费模块里注册为 Bean：
     * <pre>{@code
     * @Bean
     * Declarables notificationQueue() {
     *     return EventQueues.declare("notification.create", "like.created", "comment.created");
     * }
     * }</pre>
     *
     * @param queue       队列名，格式为 {@code <消费模块>.<用途>}
     * @param routingKeys 要订阅的路由键，可以用 topic 通配符
     */
    public static Declarables declare(String queue, String... routingKeys) {
        String deadLetterQueue = queue + ".dlq";
        Queue main = QueueBuilder.durable(queue).classic()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
        Queue dead = QueueBuilder.durable(deadLetterQueue).classic().build();
        List<Declarable> declarables = new ArrayList<>(List.of(main, dead,
                new Binding(deadLetterQueue, Binding.DestinationType.QUEUE, DEAD_LETTER_EXCHANGE, deadLetterQueue,
                        null)));
        for (String routingKey : routingKeys) {
            declarables.add(new Binding(queue, Binding.DestinationType.QUEUE, EXCHANGE, routingKey, null));
        }
        return new Declarables(declarables);
    }

    static TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    static DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
}
