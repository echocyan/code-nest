package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.echocyan.codenest.common.util.DateTimes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * 发布领域事件的唯一入口，投递语义为"至少一次"，重复消息由消费端幂等处理（见 {@link IdempotentConsumer}）。
 * <p>
 * {@link #publish} 必须在写库的同一个事务里调用：在当前事务里写入一条 {@code mq_outbox} 记录，方法本身不访问 broker，
 * 事件与业务数据一起提交或回滚。事务提交后（afterCommit）立即发送，收到 publisher confirm 后把记录标记为 SENT；
 * 发送失败或迟迟没有 confirm 的记录，由 {@link OutboxRelay} 按指数退避补发。没有活跃事务时直接抛出异常，
 * 忘了加 {@code @Transactional} 的调用在测试里就会暴露。
 */
@Slf4j
@Component
public class DomainEventPublisher {

    /**
     * 等待 publisher confirm 的时间。
     */
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

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
     * 在当前事务里发布事件，路由键取自事件类上的 {@link DomainEvent}。
     *
     * @throws IllegalArgumentException 事件类没有标注 {@link DomainEvent}
     * @throws IllegalStateException    没有活跃事务
     */
    public void publish(Object event) {
        DomainEvent domainEvent = event.getClass().getAnnotation(DomainEvent.class);
        if (domainEvent == null) {
            throw new IllegalArgumentException(event.getClass().getName() + " is not annotated with @DomainEvent");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Domain events must be published in a transaction: "
                    + event.getClass().getName());
        }
        MqOutbox outbox = new MqOutbox();
        outbox.setRoutingKey(domainEvent.value());
        outbox.setEventType(event.getClass().getName());
        outbox.setPayload(jsonMapper.writeValueAsString(event));
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
}
