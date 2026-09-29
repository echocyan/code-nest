package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * 把已收到 confirm 的 Outbox 记录批量标记为 SENT。
 * <p>
 * 每条 confirm 单独写一次库，高负载下写入跟不上 confirm 的速度，积压的标记在应用停止时丢失，
 * 这些记录会被补发任务当作发送失败重新投递。所以 confirm 回调只把 ID 放进队列，每 100 毫秒合并成
 * {@code UPDATE … WHERE id IN (…)}；应用停止前把队列里剩下的全部写完。仍然丢失的标记（如进程被杀）
 * 只会导致重复投递，由消费端幂等处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxSentMarker {

    static final int BATCH_SIZE = 500;

    private final Queue<Long> pending = new ConcurrentLinkedQueue<>();

    private final MqOutboxMapper outboxMapper;

    /**
     * 登记一条已确认的记录，在 AMQP 连接线程上调用，不访问数据库。
     */
    void markSent(long id) {
        pending.add(id);
    }

    @Scheduled(fixedDelay = 100, initialDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    public void flush() {
        List<Long> batch = new ArrayList<>(BATCH_SIZE);
        Long id;
        while ((id = pending.poll()) != null) {
            batch.add(id);
            if (batch.size() == BATCH_SIZE) {
                update(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            update(batch);
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    private void update(List<Long> ids) {
        try {
            ChainWrappers.lambdaUpdateChain(outboxMapper)
                    .in(MqOutbox::getId, ids)
                    .eq(MqOutbox::getStatus, OutboxStatus.PENDING)
                    .set(MqOutbox::getStatus, OutboxStatus.SENT)
                    .update(new MqOutbox());
        } catch (RuntimeException e) {
            // 标记失败只会导致重复投递，不重试，以免失败时队列无限增长
            log.warn("Failed to mark {} outbox events as sent, they will be relayed again", ids.size(), e);
        }
    }
}
