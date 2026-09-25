package com.echocyan.codenest.framework.mq;

import java.lang.annotation.*;

/**
 * 标记可以经 {@link DomainEventPublisher} 发出的事件类，并声明它的路由键。
 * 事件类一般是 record，只带 ID 和少量常用字段，放在生产模块的 {@code api/event/} 下。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEvent {

    /**
     * 路由键，格式为 {@code <生产模块>.<事件>}，例如 {@code article.published}。
     */
    String value();
}
