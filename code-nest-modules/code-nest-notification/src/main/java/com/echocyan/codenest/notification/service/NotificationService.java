package com.echocyan.codenest.notification.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.notification.NotificationErrorCode;
import com.echocyan.codenest.notification.entity.Notification;
import com.echocyan.codenest.notification.vo.NotificationVO;

/**
 * 站内通知的写入与查询。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 未读数的上限。
     */
    int MAX_UNREAD_COUNT = 100;

    /**
     * 写入一条通知。触发者就是接收者时跳过；dedupKey 已存在时忽略。
     */
    void send(Notification notification);

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
