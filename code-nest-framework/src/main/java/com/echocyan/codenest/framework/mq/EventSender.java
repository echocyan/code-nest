package com.echocyan.codenest.framework.mq;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 按约定的消息格式把事件发往 {@link EventQueues#EXCHANGE}：messageId 放在 {@code message_id} 属性，
 * 事件类型放在 {@code type} 属性，body 是事件的 JSON，消息持久化。
 */
@Component
@RequiredArgsConstructor
class EventSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送一条消息，返回 broker 的 confirm 结果：true 为 ack，false 为 nack。
     * 连接失败等发送异常也体现为异常完成的 future，不直接抛出。
     */
    CompletableFuture<Boolean> send(String messageId, String routingKey, String eventType, String payload) {
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .setType(eventType)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(messageId);
        try {
            rabbitTemplate.send(EventQueues.EXCHANGE, routingKey, message, correlation);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return correlation.getFuture().thenApply(CorrelationData.Confirm::ack);
    }
}
