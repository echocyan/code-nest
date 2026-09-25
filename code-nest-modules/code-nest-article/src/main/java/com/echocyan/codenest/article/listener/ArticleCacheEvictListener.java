package com.echocyan.codenest.article.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.framework.mq.EventQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：文章编辑、删除后第二次删除缓存。事件经 Outbox 发出并带重试，第二次删除一定会执行；
 * MQ 的投递延迟起到了延迟双删中"延迟"的作用。删除缓存本身幂等，不加 {@code @IdempotentConsumer}。
 */
@Component
@RabbitListener(queues = ArticleCacheEvictListener.QUEUE)
@RequiredArgsConstructor
public class ArticleCacheEvictListener {

    static final String QUEUE = "article.cache-evict";

    private final ArticleService articleService;

    @Bean
    static Declarables articleCacheEvictQueue() {
        return EventQueues.declare(QUEUE, "article.updated", "article.deleted");
    }

    @RabbitHandler
    public void onUpdated(ArticleUpdatedEvent event) {
        articleService.evictCache(event.articleId());
    }

    @RabbitHandler
    public void onDeleted(ArticleDeletedEvent event) {
        articleService.evictCache(event.articleId());
    }
}
