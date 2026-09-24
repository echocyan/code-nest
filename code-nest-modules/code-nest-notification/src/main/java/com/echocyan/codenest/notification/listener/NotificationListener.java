package com.echocyan.codenest.notification.listener;

import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import com.echocyan.codenest.framework.mq.IdempotentConsumer;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.entity.NotificationType;
import com.echocyan.codenest.notification.service.NotificationService;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 消费 {@value #QUEUE}：按事件类型确定接收者，生成通知。
 */
@Component
@RabbitListener(queues = NotificationListener.QUEUE)
@RequiredArgsConstructor
public class NotificationListener {

    static final String QUEUE = "notification.create";

    private final NotificationService notificationService;

    @Bean
    static Declarables notificationQueue() {
        return EventQueues.declare(QUEUE, "like.created", "comment.created", "follow.created");
    }

    /**
     * 通知文章作者；同一人对同一篇文章只通知一次。
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onLike(LikeCreatedEvent event) {
        Notification notification = new Notification();
        notification.setRecipientId(event.authorId());
        notification.setActorId(event.userId());
        notification.setType(NotificationType.LIKE);
        notification.setArticleId(event.articleId());
        notification.setDedupKey("L:" + event.userId() + ":" + event.articleId());
        notificationService.send(notification);
    }

    /**
     * 评论通知文章作者，回复通知被回复的人。评论、回复各自独立，不去重。
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onComment(CommentCreatedEvent event) {
        boolean reply = event.rootId() != 0;
        Notification notification = new Notification();
        notification.setRecipientId(reply ? event.replyToUserId() : event.articleAuthorId());
        notification.setActorId(event.userId());
        notification.setType(reply ? NotificationType.REPLY : NotificationType.COMMENT);
        notification.setArticleId(event.articleId());
        notification.setCommentId(event.commentId());
        notificationService.send(notification);
    }

    /**
     * 通知被关注者；同一人只通知一次。
     */
    @RabbitHandler
    @IdempotentConsumer
    public void onFollow(FollowCreatedEvent event) {
        Notification notification = new Notification();
        notification.setRecipientId(event.authorId());
        notification.setActorId(event.followerId());
        notification.setType(NotificationType.FOLLOW);
        notification.setDedupKey("F:" + event.followerId() + ":" + event.authorId());
        notificationService.send(notification);
    }
}
