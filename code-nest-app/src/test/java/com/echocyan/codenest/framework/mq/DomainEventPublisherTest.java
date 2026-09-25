package com.echocyan.codenest.framework.mq;

import com.echocyan.codenest.support.IntegrationTest;
import com.echocyan.codenest.support.SharedContainers;
import com.echocyan.codenest.support.probe.MqProbe;
import com.echocyan.codenest.support.probe.MqProbe.ProbeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventPublisherTest extends IntegrationTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static String nonce() {
        return UUID.randomUUID().toString();
    }

    @Test
    void publishesWithoutTransactionImmediately() {
        String nonce = nonce();

        publisher.publish(new ProbeEvent(nonce));

        Message message = receive(nonce, Duration.ofSeconds(5));
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
        assertThat(message.getMessageProperties().getType()).isEqualTo(ProbeEvent.class.getName());
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("probe.happened");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"nonce\":\"" + nonce + "\"}");
    }

    @Test
    void publishesInTransactionOnlyAfterCommit() {
        String nonce = nonce();

        transactionTemplate.executeWithoutResult(status -> {
            publisher.publish(new ProbeEvent(nonce));
            assertThat(receive(nonce, Duration.ofSeconds(1))).isNull();
        });

        assertThat(receive(nonce, Duration.ofSeconds(5))).isNotNull();
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() {
        String rolledBack = nonce();
        String marker = nonce();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publish(new ProbeEvent(rolledBack));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        publisher.publish(new ProbeEvent(marker));

        // 按到达顺序拉取：先于 marker 到达的消息里不应有被回滚的事件
        assertThat(receive(marker, Duration.ofSeconds(5), rolledBack)).isNotNull();
    }

    @Test
    void relaysEventsWrittenWhileBrokerWasDown() throws Exception {
        String nonce = nonce();

        SharedContainers.RABBIT.execInContainer("rabbitmqctl", "stop_app");
        try {
            transactionTemplate.executeWithoutResult(status -> publisher.publish(new ProbeEvent(nonce)));
        } finally {
            SharedContainers.RABBIT.execInContainer("rabbitmqctl", "start_app");
        }

        assertThat(receive(nonce, Duration.ofSeconds(60))).isNotNull();
    }

    @Test
    void rejectsEventClassWithoutDomainEventAnnotation() {
        assertThatThrownBy(() -> publisher.publish(new Undeclared("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsWhenNoQueueIsBoundToRoutingKey() {
        assertThatThrownBy(() -> publisher.publish(new Unbound(nonce())))
                .isInstanceOf(AmqpException.class);
    }

    private Message receive(String nonce, Duration timeout) {
        return receive(nonce, timeout, null);
    }

    /**
     * 从 {@link MqProbe#INBOX} 按到达顺序拉取，丢弃其他测试留下的消息，直到拿到带 nonce 的消息或超时。
     * 在此之前如果拉到带 forbiddenNonce 的消息，直接判定失败。
     */
    private Message receive(String nonce, Duration timeout, String forbiddenNonce) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Message message = rabbitTemplate.receive(MqProbe.INBOX, 200);
            if (message == null) {
                continue;
            }
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            assertThat(forbiddenNonce == null || !body.contains(forbiddenNonce))
                    .as("received rolled back event %s", forbiddenNonce).isTrue();
            if (body.contains(nonce)) {
                return message;
            }
        }
        return null;
    }

    private record Undeclared(String value) {
    }

    @DomainEvent("probe.unbound")
    record Unbound(String nonce) {
    }
}
