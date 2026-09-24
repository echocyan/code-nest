package com.echocyan.codenest.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 发给 recipientId 的一条通知，由 actorId 的操作触发。
 */
@Getter
@Setter
@TableName("notification")
public class Notification extends AuditableEntity {

    private Long id;

    private Long recipientId;

    private Long actorId;

    private NotificationType type;

    /** 关注通知为 null。 */
    private Long articleId;

    /** 评论或回复本身的 ID；只有评论、回复通知有。 */
    private Long commentId;

    /** 点赞、关注用来去重，评论、回复为 null。 */
    private String dedupKey;

    private Boolean isRead;
}
