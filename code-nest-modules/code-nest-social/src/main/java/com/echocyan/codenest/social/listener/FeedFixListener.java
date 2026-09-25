package com.echocyan.codenest.social.listener;

import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import com.echocyan.codenest.social.api.event.FollowDeletedEvent;
import com.echocyan.codenest.social.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：关注、取关、删文后修正发件箱与收件箱。各修正本身幂等，不加 {@code @IdempotentConsumer}。
 */
@Component
@RabbitListener(queues = FeedFixListener.QUEUE)
@RequiredArgsConstructor
public class FeedFixListener {

    static final String QUEUE = "social.feed-fix";

    private final FeedFanoutService feedFanoutService;

    @Bean
    static Declarables feedFixQueue() {
        return EventQueues.declare(QUEUE, "follow.created", "follow.deleted", "article.deleted");
    }

    @RabbitHandler
    public void onFollow(FollowCreatedEvent event) {
        feedFanoutService.mergeOnFollow(event.followerId(), event.authorId());
    }

    @RabbitHandler
    public void onUnfollow(FollowDeletedEvent event) {
        feedFanoutService.removeOnUnfollow(event.followerId(), event.authorId());
    }

    @RabbitHandler
    public void onArticleDeleted(ArticleDeletedEvent event) {
        feedFanoutService.removeArticle(event.articleId(), event.authorId());
    }
}
