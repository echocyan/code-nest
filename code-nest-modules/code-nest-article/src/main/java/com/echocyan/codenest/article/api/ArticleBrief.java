package com.echocyan.codenest.article.api;

import java.time.LocalDateTime;

/**
 * 在列表、Feed、通知等处展示的文章摘要，不含正文。
 */
public record ArticleBrief(
        Long id,
        Long authorId,
        Long categoryId,
        String title,
        String summary,
        String coverUrl,
        ArticleStatus status,
        LocalDateTime publishedAt) {
}
