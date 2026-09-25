package com.echocyan.codenest.social.listener;

import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.social.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：把新发布的文章写入发件箱并推送给粉丝。推送本身幂等，不加 {@code @IdempotentConsumer}。
 */
@Component
@RequiredArgsConstructor
public class FeedPushListener {

    static final String QUEUE = "social.feed-push";

    private final FeedFanoutService feedFanoutService;

    @Bean
    static Declarables feedPushQueue() {
        return EventQueues.declare(QUEUE, "article.published");
    }

    @RabbitListener(queues = QUEUE)
    public void onPublished(ArticlePublishedEvent event) {
        feedFanoutService.push(event.articleId(), event.authorId());
    }
}
