package com.echocyan.codenest.article.api;

/**
 * 在通知等处展示的评论或回复摘要。
 *
 * @param rootId 0 表示评论，否则是该回复所属评论的 ID
 */
public record CommentBrief(Long id, Long articleId, Long userId, Long rootId, String content) {
}
