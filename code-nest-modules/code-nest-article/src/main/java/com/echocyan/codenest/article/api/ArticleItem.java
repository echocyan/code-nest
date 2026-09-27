package com.echocyan.codenest.article.api;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 文章列表项：摘要、分类、作者简要信息与计数，不含正文和标签。文章列表、热榜、关注 Feed、搜索、收藏列表都用这个形状。
 *
 * @param author 作者简要信息；作者不存在时为 null
 */
public record ArticleItem(
        Long id,
        String title,
        String summary,
        String coverUrl,
        CategoryBrief category,
        ArticleStatus status,
        LocalDateTime publishedAt,
        UserBrief author,
        ArticleCounts counts) {
}
