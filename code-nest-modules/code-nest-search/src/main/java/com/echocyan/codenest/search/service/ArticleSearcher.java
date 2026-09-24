package com.echocyan.codenest.search.service;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.dto.SearchSort;

/**
 * 在已发布文章中按关键词检索，由 {@code search.mode} 选择实现。调用方已校验翻页深度。
 */
public interface ArticleSearcher {

    /**
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    PageResult<Hit> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page, long size);

    /**
     * 一条命中结果。
     *
     * @param titleHighlight   高亮后的标题，不支持高亮时为 null
     * @param contentHighlight 正文的高亮片段，不支持高亮时为 null
     */
    record Hit(ArticleBrief article, String titleHighlight, String contentHighlight) {
    }
}
