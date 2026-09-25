package com.echocyan.codenest.search.listener;

import com.echocyan.codenest.framework.mq.EventQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 声明 {@value #QUEUE}。消费者只在 es 档装配，队列则两档都声明：article 事件发送时要求至少有一个队列绑定；
 * mysql-like 档下积压的消息，切到 es 档后被消费，补上这期间的变更。
 */
@Configuration(proxyBeanMethods = false)
class ArticleSyncQueue {

    static final String QUEUE = "search.article-sync";

    @Bean
    static Declarables articleSyncQueueDeclarables() {
        return EventQueues.declare(QUEUE, "article.published", "article.updated", "article.deleted");
    }
}
