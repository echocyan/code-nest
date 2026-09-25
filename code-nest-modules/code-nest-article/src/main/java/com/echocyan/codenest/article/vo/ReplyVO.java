package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 评论下的一条回复。
 *
 * @param rootId  所属评论的 ID
 * @param author  回复者简要信息；用户不存在时为 null
 * @param replyTo 被回复人的简要信息；直接回复评论时为 null
 */
public record ReplyVO(
        Long id,
        Long rootId,
        String content,
        UserBrief author,
        UserBrief replyTo,
        LocalDateTime createdAt) {
}
