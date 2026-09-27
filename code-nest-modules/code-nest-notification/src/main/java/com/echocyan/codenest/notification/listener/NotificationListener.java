package com.echocyan.codenest.notification.listener;

import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.framework.mq.IdempotentConsumer;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：把点赞、评论（含回复）、关注事件转给 {@link NotificationService}，由它决定接收者与去重。
 */
@Component
@RabbitListener(queues = NotificationListener.QUEUE)
@RequiredArgsConstructor
public class NotificationListener {

    static final String QUEUE = "notification.create";

    private final NotificationService notificationService;

    @Bean
    static Declarables notificationQueue() {
        return EventQueues.declare(QUEUE, NotificationListener.class);
    }

    @RabbitHandler
    @IdempotentConsumer
    public void onLike(LikeCreatedEvent event) {
        notificationService.notifyLike(event);
    }

    @RabbitHandler
    @IdempotentConsumer
    public void onComment(CommentCreatedEvent event) {
        notificationService.notifyComment(event);
    }

    @RabbitHandler
    @IdempotentConsumer
    public void onFollow(FollowCreatedEvent event) {
        notificationService.notifyFollow(event);
    }
}
