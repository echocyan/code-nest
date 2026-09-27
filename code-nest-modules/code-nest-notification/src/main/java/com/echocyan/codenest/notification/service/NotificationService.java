package com.echocyan.codenest.notification.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.event.CommentCreatedEvent;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.notification.NotificationErrorCode;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.vo.NotificationVO;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;

/**
 * 站内通知的写入与查询。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 未读数的上限。
     */
    int MAX_UNREAD_COUNT = 100;

    /**
     * 点赞通知文章作者；同一人对同一篇文章只通知一次，给自己的文章点赞不通知。
     */
    void notifyLike(LikeCreatedEvent event);

    /**
     * 评论通知文章作者，回复通知被回复的人；评论、回复各自独立，不去重。评论自己的文章、回复自己不通知。
     */
    void notifyComment(CommentCreatedEvent event);

    /**
     * 关注通知被关注者；同一人只通知一次。
     */
    void notifyFollow(FollowCreatedEvent event);

    /**
     * 按 ID 倒序翻阅自己的通知，展示信息在读取时组装。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     */
    CursorResult<NotificationVO> listMine(long recipientId, Long cursor, int size);

    /**
     * 未读通知数，最多数到 {@value #MAX_UNREAD_COUNT}，前端据此显示"99+"。
     */
    int countUnread(long recipientId);

    /**
     * 把自己的一条通知标为已读，已读的再标一次不报错。
     *
     * @throws BizException {@link NotificationErrorCode#NOTIFICATION_NOT_FOUND} 通知不存在或不属于自己
     */
    void markRead(long recipientId, long notificationId);

    /**
     * 把自己的全部未读通知标为已读。
     */
    void markAllRead(long recipientId);
}
