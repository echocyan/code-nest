package com.echocyan.codenest.framework.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
     * 声明一个消费队列及其死信队列，并按 listener 里消费这个队列的方法订阅事件：每个方法的事件参数（类型标注了
     * {@link DomainEvent}）决定一个路由键，订阅关系因此只由方法签名决定，不会与处理方法对不上。在消费模块里注册为 Bean：
     * <pre>{@code
     * @Bean
     * static Declarables notificationQueue() {
     *     return EventQueues.declare(QUEUE, NotificationListener.class);
     * }
     * }</pre>
     * 类上标注 {@link RabbitListener} 时看它的 {@link RabbitHandler} 方法，否则看 {@code queues} 含该队列的
     * {@link RabbitListener} 方法。
     *
     * @param queue 队列名，格式为 {@code <消费模块>.<用途>}
     * @throws IllegalStateException listener 里没有消费这个队列的方法，或某个方法没有事件参数
     */
    public static Declarables declare(String queue, Class<?> listener) {
        return declareEvents(queue, eventTypesOf(queue, listener).toArray(Class<?>[]::new));
    }

    /**
     * 声明一个消费队列及其死信队列，按给定的事件类型订阅；不给事件类型时只声明队列、不绑定。
     * 队列在应用里有消费方法时用 {@link #declare(String, Class)}。
     *
     * @param eventTypes 标注了 {@link DomainEvent} 的事件类
     * @throws IllegalStateException 事件类没有标注 {@link DomainEvent}
     */
    public static Declarables declareEvents(String queue, Class<?>... eventTypes) {
        String deadLetterQueue = queue + ".dlq";
        Queue main = QueueBuilder.durable(queue).classic()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
        Queue dead = QueueBuilder.durable(deadLetterQueue).classic().build();
        List<Declarable> declarables = new ArrayList<>(List.of(main, dead,
                new Binding(deadLetterQueue, Binding.DestinationType.QUEUE, DEAD_LETTER_EXCHANGE, deadLetterQueue,
                        null)));
        for (Class<?> eventType : eventTypes) {
            declarables.add(new Binding(queue, Binding.DestinationType.QUEUE, EXCHANGE, routingKeyOf(eventType),
                    null));
        }
        return new Declarables(declarables);
    }

    /**
     * @throws IllegalStateException 事件类没有标注 {@link DomainEvent}
     */
    static String routingKeyOf(Class<?> eventType) {
        DomainEvent domainEvent = eventType.getAnnotation(DomainEvent.class);
        if (domainEvent == null) {
            throw new IllegalStateException(eventType.getName() + " is not annotated with @DomainEvent");
        }
        return domainEvent.value();
    }

    private static List<Class<?>> eventTypesOf(String queue, Class<?> listener) {
        RabbitListener classListener = AnnotatedElementUtils.findMergedAnnotation(listener, RabbitListener.class);
        if (classListener != null && !List.of(classListener.queues()).contains(queue)) {
            throw new IllegalStateException(listener.getName() + " does not listen to " + queue);
        }
        List<Class<?>> eventTypes = Stream.of(ReflectionUtils.getUniqueDeclaredMethods(listener))
                .filter(method -> classListener != null
                        ? method.isAnnotationPresent(RabbitHandler.class)
                        : listensTo(method, queue))
                .map(EventQueues::eventTypeOf)
                .distinct()
                .toList();
        if (eventTypes.isEmpty()) {
            throw new IllegalStateException(listener.getName() + " has no handler for " + queue);
        }
        return eventTypes;
    }

    private static boolean listensTo(Method method, String queue) {
        RabbitListener rabbitListener = AnnotatedElementUtils.findMergedAnnotation(method, RabbitListener.class);
        return rabbitListener != null && List.of(rabbitListener.queues()).contains(queue);
    }

    private static Class<?> eventTypeOf(Method method) {
        return Stream.of(method.getParameterTypes())
                .filter(type -> type.isAnnotationPresent(DomainEvent.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        method.toGenericString() + " has no parameter annotated with @DomainEvent"));
    }

    static TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    static DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
}
