package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.echocyan.codenest.common.util.DateTimes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox 的补发与清理。
 * <p>
 * 补发用 {@code SELECT … FOR UPDATE SKIP LOCKED} 锁住到期的 PENDING 记录，多实例之间不会重复处理同一行。
 * 失败按指数退避重算下次补发时间，累计失败 {@link #MAX_RETRIES} 次标记为 FAILED 并打告警日志，之后由人工处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OutboxRelay {

    static final int MAX_RETRIES = 10;

    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(10);

    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);

    private static final Duration SENT_RETENTION = Duration.ofDays(7);

    private static final int BATCH_SIZE = 100;

    private static final int PURGE_BATCH_SIZE = 1000;

    private final MqOutboxMapper outboxMapper;
    private final EventSender sender;
    private final TransactionTemplate transactionTemplate;

    /**
     * 已失败 retryCount 次的记录下次补发的时间：首次延迟 {@link #FIRST_RETRY_DELAY}，之后每次翻倍，
     * 不超过 {@link #MAX_RETRY_DELAY}。
     */
    static LocalDateTime nextRetryAt(LocalDateTime now, int retryCount) {
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(retryCount, 16));
        return now.plus(delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay);
    }

    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.SECONDS)
    public void relayDue() {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = DateTimes.now();
            List<MqOutbox> due = ChainWrappers.lambdaQueryChain(outboxMapper)
                    .eq(MqOutbox::getStatus, OutboxStatus.PENDING)
                    .le(MqOutbox::getNextRetryAt, now)
                    .orderByAsc(MqOutbox::getNextRetryAt)
                    .last("LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED")
                    .list();
            if (due.isEmpty()) {
                return;
            }
            // 先全部发出，再统一等待 confirm
            List<CompletableFuture<Boolean>> confirms = due.stream()
                    .map(outbox -> sender.send(String.valueOf(outbox.getId()), outbox.getRoutingKey(),
                            outbox.getEventType(), outbox.getPayload()))
                    .toList();
            awaitAll(confirms);
            for (int i = 0; i < due.size(); i++) {
                MqOutbox outbox = due.get(i);
                if (isAcked(confirms.get(i))) {
                    outbox.setStatus(OutboxStatus.SENT);
                } else {
                    recordFailure(outbox, now);
                }
                outboxMapper.updateById(outbox);
            }
        });
    }

    /**
     * 每小时删除一次保留期已过的 SENT 记录，分批删除以免长时间锁表。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeSent() {
        LocalDateTime before = DateTimes.now().minus(SENT_RETENTION);
        int deleted;
        do {
            deleted = outboxMapper.delete(Wrappers.lambdaQuery(MqOutbox.class)
                    .eq(MqOutbox::getStatus, OutboxStatus.SENT)
                    .lt(MqOutbox::getCreatedAt, before)
                    .last("LIMIT " + PURGE_BATCH_SIZE));
        } while (deleted == PURGE_BATCH_SIZE);
    }

    private void recordFailure(MqOutbox outbox, LocalDateTime now) {
        int retryCount = outbox.getRetryCount() + 1;
        outbox.setRetryCount(retryCount);
        if (retryCount >= MAX_RETRIES) {
            outbox.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event failed {} times and needs manual handling: id={}, routingKey={}",
                    retryCount, outbox.getId(), outbox.getRoutingKey());
        } else {
            outbox.setNextRetryAt(nextRetryAt(now, retryCount));
            log.warn("Outbox event relay failed {} times: id={}, routingKey={}",
                    retryCount, outbox.getId(), outbox.getRoutingKey());
        }
    }

    /**
     * 等所有 confirm 完成或超时；个别失败不影响其余记录，结果由 {@link #isAcked} 逐条判断。
     */
    private static void awaitAll(List<CompletableFuture<Boolean>> confirms) {
        try {
            CompletableFuture.allOf(confirms.toArray(CompletableFuture[]::new))
                    .get(DomainEventPublisher.CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            // 逐条判断
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isAcked(CompletableFuture<Boolean> confirm) {
        return confirm.state() == Future.State.SUCCESS && confirm.resultNow();
    }
}
