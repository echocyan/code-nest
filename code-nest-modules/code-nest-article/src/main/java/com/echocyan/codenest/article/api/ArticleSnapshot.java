package com.echocyan.codenest.article.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章某一版本的完整内容，供搜索索引使用。
 *
 * @param content  Markdown 原文
 * @param tagIds   按 ID 正序
 * @param tagNames 与 tagIds 一一对应
 * @param version  每次编辑、发布、删除都 +1，可用作外部版本号
 * @param deleted  已删除的文章也有快照，以便同步方拿到删除后的版本号
 */
public record ArticleSnapshot(
        Long id,
        Long authorId,
        Long categoryId,
        String title,
        String summary,
        String content,
        List<Long> tagIds,
        List<String> tagNames,
        ArticleStatus status,
        LocalDateTime publishedAt,
        int version,
        boolean deleted) {

    /**
     * 已发布且未删除，即应当能被搜到。
     */
    public boolean searchable() {
        return status == ArticleStatus.PUBLISHED && !deleted;
    }
}
