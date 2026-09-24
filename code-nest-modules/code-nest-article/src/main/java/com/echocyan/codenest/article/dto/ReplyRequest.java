package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表回复。
 *
 * @param replyToUserId 回复 @某人，须是该评论或同一评论下某条回复的作者；不填时，回复评论即回复评论本身，
 *                      回复某条回复即 @ 该回复的作者
 */
public record ReplyRequest(@NotBlank @Size(max = 1000) String content, Long replyToUserId) {
}
