package com.echocyan.codenest.article.api;

/**
 * 在通知等处展示的评论或回复摘要。
 *
 * @param rootId  0 表示评论，否则是该回复所属评论的 ID
 * @param summary 内容开头至多 {@value #SUMMARY_LENGTH} 个字
 */
public record CommentBrief(Long id, Long articleId, Long userId, Long rootId, String summary) {

    public static final int SUMMARY_LENGTH = 100;
}
