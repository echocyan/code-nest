package com.echocyan.codenest.support.probe;

import com.echocyan.codenest.framework.mq.DomainEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.framework.mq.IdempotentConsumer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 仅存在于测试类路径，用来验证 framework 的消息可靠性底座：
 * {@link #INBOX} 只绑定不消费，测试主动拉取；{@link #CONSUMER} 由带 {@link IdempotentConsumer} 的监听器消费。
 */
@Component
public class MqProbe {

    public static final String INBOX = "probe.inbox";

    public static final String CONSUMER = "probe.consumer";

    @DomainEvent("probe.happened")
    public record ProbeEvent(String nonce) {
    }

    /** 每个 nonce 还要失败的次数。 */
    private final Map<String, AtomicInteger> pendingFailures = new ConcurrentHashMap<>();

    /** 每个 nonce 被调用的次数，包括失败的调用。 */
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    /** 每个 nonce 被成功处理的次数。 */
    private final Map<String, AtomicInteger> processed = new ConcurrentHashMap<>();

    @Bean
    static Declarables probeInboxQueue() {
        return EventQueues.declare(INBOX, "probe.happened");
    }

    /**
     * 不绑定路由键，测试经默认交换机直接投递，以便控制 messageId。
     */
    @Bean
    static Declarables probeConsumerQueue() {
        return EventQueues.declare(CONSUMER);
    }

    @RabbitListener(queues = CONSUMER)
    @IdempotentConsumer
    public void consume(ProbeEvent event) {
        counter(attempts, event.nonce()).incrementAndGet();
        AtomicInteger failures = pendingFailures.get(event.nonce());
        if (failures != null && failures.getAndDecrement() > 0) {
            throw new IllegalStateException("probe failure for " + event.nonce());
        }
        counter(processed, event.nonce()).incrementAndGet();
    }

    /**
     * 让带这个 nonce 的消息在前 times 次处理时抛异常。
     */
    public void failTimes(String nonce, int times) {
        pendingFailures.put(nonce, new AtomicInteger(times));
    }

    public int attempts(String nonce) {
        return counter(attempts, nonce).get();
    }

    public int processed(String nonce) {
        return counter(processed, nonce).get();
    }

    private static AtomicInteger counter(Map<String, AtomicInteger> counters, String nonce) {
        return counters.computeIfAbsent(nonce, key -> new AtomicInteger());
    }
}
