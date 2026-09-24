package com.echocyan.codenest.framework.mq;

import java.lang.reflect.UndeclaredThrowableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link IdempotentConsumer} 的实现。messageId 与消费队列名取自当前线程正在处理的消息，
 * 由 {@link MqConfig} 注册的接收后处理器在监听器执行前记下。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
class IdempotentConsumerAspect {

    /**
     * 监听器线程只用来处理消息，每收到一条就覆盖一次，不需要清理。
     */
    private static final ThreadLocal<Message> CONSUMING = new ThreadLocal<>();

    private final MqConsumeRecordMapper consumeRecordMapper;
    private final TransactionTemplate transactionTemplate;

    static Message remember(Message message) {
        CONSUMING.set(message);
        return message;
    }

    @Around("@annotation(com.echocyan.codenest.framework.mq.IdempotentConsumer)")
    public Object consumeOnce(ProceedingJoinPoint joinPoint) {
        Message message = CONSUMING.get();
        if (message == null) {
            throw new IllegalStateException("@IdempotentConsumer must be used on a @RabbitListener method: "
                    + joinPoint.getSignature());
        }
        MessageProperties properties = message.getMessageProperties();
        if (properties.getMessageId() == null) {
            throw new IllegalStateException("Message has no message_id, queue=" + properties.getConsumerQueue());
        }
        MqConsumeRecord record = new MqConsumeRecord();
        record.setMessageId(properties.getMessageId());
        record.setConsumer(properties.getConsumerQueue());
        return transactionTemplate.execute(status -> {
            try {
                consumeRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // MySQL 只回滚这一条语句，事务可以正常结束
                log.info("Skip consumed message: messageId={}, consumer={}", record.getMessageId(), record.getConsumer());
                return null;
            }
            return proceed(joinPoint);
        });
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new UndeclaredThrowableException(e);
        }
    }
}
