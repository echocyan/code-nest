package com.echocyan.codenest.search.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.search.service.ArticleIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：文章发布、编辑、删除后，按最新状态同步到搜索索引。
 * 同步按外部版本号写入，本身幂等，不加 {@code @IdempotentConsumer}。
 */
@Component
@RabbitListener(queues = ArticleSyncListener.QUEUE)
@RequiredArgsConstructor
public class ArticleSyncListener {

    static final String QUEUE = "search.article-sync";

    private final ArticleIndex articleIndex;

    @Bean
    static Declarables articleSyncQueue() {
        return EventQueues.declare(QUEUE, ArticleSyncListener.class);
    }

    @RabbitHandler
    public void onPublished(ArticlePublishedEvent event) {
        articleIndex.sync(event.articleId());
    }

    @RabbitHandler
    public void onUpdated(ArticleUpdatedEvent event) {
        articleIndex.sync(event.articleId());
    }

    @RabbitHandler
    public void onDeleted(ArticleDeletedEvent event) {
        articleIndex.sync(event.articleId());
    }
}
