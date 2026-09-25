package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情。
 *
 * @param author 作者简要信息；作者不存在时为 null
 */
public record ArticleDetailVO(
        Long id,
        String title,
        String summary,
        String coverUrl,
        String content,
        ArticleStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version,
        CategoryVO category,
        List<TagVO> tags,
        UserBrief author,
        ArticleCountsVO counts) {
}
