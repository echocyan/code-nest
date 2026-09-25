package com.echocyan.codenest.framework.mq;

import com.echocyan.codenest.support.IntegrationTest;
import com.echocyan.codenest.support.probe.MqProbe;
import com.echocyan.codenest.support.probe.MqProbe.ProbeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class IdempotentConsumerTest extends IntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MqProbe probe;

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    @Test
    void processesSameMessageIdOnlyOnce() {
        String duplicated = nonce();
        String marker = nonce();
        String messageId = nonce();

        send(messageId, duplicated);
        send(messageId, duplicated);
        send(nonce(), marker);

        // 单个消费者按顺序处理，marker 处理完时重复消息也已处理过
        await().atMost(Duration.ofSeconds(10)).until(() -> probe.processed(marker) == 1);
        assertThat(probe.processed(duplicated)).isEqualTo(1);
    }

    @Test
    void failedAttemptDoesNotCountAsConsumed() {
        String nonce = nonce();
        probe.failTimes(nonce, 2);

        send(nonce(), nonce);

        await().atMost(Duration.ofSeconds(10)).until(() -> probe.processed(nonce) == 1);
        assertThat(probe.attempts(nonce)).isEqualTo(3);
    }

    @Test
    void movesMessageToDeadLetterQueueAfterRetriesExhausted() {
        String nonce = nonce();
        String messageId = nonce();
        probe.failTimes(nonce, Integer.MAX_VALUE);

        send(messageId, nonce);

        AtomicReference<Message> dead = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            Message message = rabbitTemplate.receive(MqProbe.CONSUMER + ".dlq");
            if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
                dead.set(message);
            }
            return dead.get() != null;
        });
        // 首次处理加 3 次重试
        assertThat(probe.attempts(nonce)).isEqualTo(4);
        assertThat(new String(dead.get().getBody(), StandardCharsets.UTF_8)).contains(nonce);
    }

    /**
     * 经默认交换机直接投递到 {@link MqProbe#CONSUMER}，消息格式与 {@link DomainEventPublisher} 发出的一致。
     */
    private void send(String messageId, String nonce) {
        Message message = MessageBuilder.withBody(("{\"nonce\":\"" + nonce + "\"}").getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .setType(ProbeEvent.class.getName())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send("", MqProbe.CONSUMER, message);
    }
}
