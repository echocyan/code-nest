package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.user.api.UserBrief;
import java.time.LocalDateTime;

/**
 * 文章下的一条评论，不内嵌回复。
 *
 * @param deleted 已删除但仍有回复的评论为 true，此时 content 为"该评论已删除"、author 为 null
 * @param author  评论者简要信息；用户不存在时为 null
 */
public record CommentVO(
        Long id,
        String content,
        boolean deleted,
        UserBrief author,
        long replyCount,
        LocalDateTime createdAt) {
}
