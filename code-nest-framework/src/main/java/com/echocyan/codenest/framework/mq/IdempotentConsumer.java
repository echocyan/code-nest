package com.echocyan.codenest.framework.mq;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 {@code @RabbitListener} 方法上，保证同一条消息在同一个消费队列上只处理一次。
 * <p>
 * 在同一个本地事务里插入 {@code mq_consume_record(message_id, consumer)} 并执行方法体，consumer 取消费队列名；
 * 唯一键冲突说明已经处理过，直接跳过。方法体抛异常时整个事务回滚，消息按重试策略重新处理。
 * 本身幂等的消费者（按 ID 覆盖写 ES、Redis {@code ZADD} 等）不需要加。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsumer {
}
