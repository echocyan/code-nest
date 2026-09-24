package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.echocyan.codenest.common.util.DateTimes;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 发布领域事件的唯一入口，投递语义为"至少一次"，重复消息由消费端幂等处理（见 {@link IdempotentConsumer}）。
 * <p>
 * <b>隐式行为：</b>{@link #publish} 按调用时有没有活跃事务走两条路径。
 * <ul>
 *     <li><b>有活跃事务</b>：在当前事务里写入一条 {@code mq_outbox} 记录，方法本身不访问 broker。
 *     事务提交后（afterCommit）立即发送，收到 publisher confirm 后把记录标记为 SENT；事务回滚则记录与消息一起消失。
 *     发送失败或迟迟没有 confirm 的记录，由 {@link OutboxRelay} 按指数退避补发。</li>
 *     <li><b>没有事务</b>：直接发送并同步等待 confirm，失败时重试；重试耗尽后抛出 {@link AmqpException}，
 *     调用方可以据此感知失败。</li>
 * </ul>
 * 业务代码应在写库的同一个事务里调用，让事件与业务数据一起提交或回滚。
 */
@Slf4j
@Component
public class DomainEventPublisher {

    /** 等待 publisher confirm 的时间。 */
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    /** 没有事务时的发送次数，含首次。 */
    private static final int DIRECT_ATTEMPTS = 3;

    private static final Duration DIRECT_BACKOFF = Duration.ofMillis(200);

    private final MqOutboxMapper outboxMapper;
    private final EventSender sender;
    private final JsonMapper jsonMapper;
    private final Executor executor;

    public DomainEventPublisher(MqOutboxMapper outboxMapper, EventSender sender, JsonMapper jsonMapper,
                                @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                                Executor executor) {
        this.outboxMapper = outboxMapper;
        this.sender = sender;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    /**
     * 发布事件，路由键取自事件类上的 {@link DomainEvent}。两条发送路径见类注释。
     *
     * @throws IllegalArgumentException 事件类没有标注 {@link DomainEvent}
     * @throws AmqpException            没有事务时，重试耗尽仍未发送成功
     */
    public void publish(Object event) {
        DomainEvent domainEvent = event.getClass().getAnnotation(DomainEvent.class);
        if (domainEvent == null) {
            throw new IllegalArgumentException(event.getClass().getName() + " is not annotated with @DomainEvent");
        }
        String routingKey = domainEvent.value();
        String eventType = event.getClass().getName();
        String payload = jsonMapper.writeValueAsString(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            publishAfterCommit(routingKey, eventType, payload);
        } else {
            publishNow(String.valueOf(IdWorker.getId()), routingKey, eventType, payload);
        }
    }

    private void publishAfterCommit(String routingKey, String eventType, String payload) {
        MqOutbox outbox = new MqOutbox();
        outbox.setRoutingKey(routingKey);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        // 留出 afterCommit 发送与 confirm 的时间，补发任务不会与之抢发
        outbox.setNextRetryAt(OutboxRelay.nextRetryAt(DateTimes.now(), 0));
        outboxMapper.insert(outbox);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendOutbox(outbox);
            }
        });
    }

    /**
     * 事务已经提交，这里的任何失败都只记日志，交给补发任务处理。
     * confirm 回调在 AMQP 连接线程上执行，标记 SENT 要写库，切换到应用线程池。
     */
    private void sendOutbox(MqOutbox outbox) {
        long id = outbox.getId();
        sender.send(String.valueOf(id), outbox.getRoutingKey(), outbox.getEventType(), outbox.getPayload())
                .whenCompleteAsync((acked, error) -> {
                    if (!Boolean.TRUE.equals(acked)) {
                        log.warn("Outbox event not confirmed, left for relay: id={}", id, error);
                        return;
                    }
                    try {
                        markSent(id);
                    } catch (RuntimeException e) {
                        log.warn("Failed to mark outbox event as sent, it will be relayed again: id={}", id, e);
                    }
                }, executor);
    }

    private void markSent(long id) {
        ChainWrappers.lambdaUpdateChain(outboxMapper)
                .eq(MqOutbox::getId, id)
                .eq(MqOutbox::getStatus, OutboxStatus.PENDING)
                .set(MqOutbox::getStatus, OutboxStatus.SENT)
                .update(new MqOutbox());
    }

    private void publishNow(String messageId, String routingKey, String eventType, String payload) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= DIRECT_ATTEMPTS; attempt++) {
            try {
                if (sender.send(messageId, routingKey, eventType, payload)
                        .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    return;
                }
                lastError = new AmqpException("Broker nacked the event");
            } catch (ExecutionException | TimeoutException e) {
                lastError = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AmqpException("Interrupted while publishing event " + routingKey, e);
            }
            log.warn("Failed to publish event, attempt {}/{}: routingKey={}, messageId={}",
                    attempt, DIRECT_ATTEMPTS, routingKey, messageId, lastError);
            if (attempt < DIRECT_ATTEMPTS) {
                sleep(DIRECT_BACKOFF.multipliedBy(attempt));
            }
        }
        throw new AmqpException("Failed to publish event " + routingKey + " after " + DIRECT_ATTEMPTS + " attempts",
                lastError);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while publishing event", e);
        }
    }
}
