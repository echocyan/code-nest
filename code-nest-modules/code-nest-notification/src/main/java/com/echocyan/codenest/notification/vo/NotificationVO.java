package com.echocyan.codenest.notification.vo;

import com.echocyan.codenest.notification.entity.NotificationType;
import com.echocyan.codenest.user.api.UserBrief;
import java.time.LocalDateTime;

/**
 * 通知列表中的一项。内容已被删除时，articleTitle、commentSummary 为"该内容已删除"。
 *
 * @param articleId      关注通知为 null
 * @param commentId      评论或回复本身的 ID，只有评论、回复通知有
 * @param commentSummary 评论或回复内容的开头，只有评论、回复通知有
 */
public record NotificationVO(
        Long id,
        NotificationType type,
        UserBrief actor,
        Long articleId,
        String articleTitle,
        Long commentId,
        String commentSummary,
        boolean read,
        LocalDateTime createdAt) {
}
