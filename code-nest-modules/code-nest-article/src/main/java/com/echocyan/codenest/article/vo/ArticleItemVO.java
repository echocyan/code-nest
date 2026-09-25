package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 文章列表项：摘要、作者简要信息与计数，不含正文和标签。
 *
 * @param author 作者简要信息；作者不存在时为 null
 */
public record ArticleItemVO(
        Long id,
        String title,
        String summary,
        String coverUrl,
        CategoryVO category,
        ArticleStatus status,
        LocalDateTime publishedAt,
        UserBrief author,
        ArticleCountsVO counts) {
}
