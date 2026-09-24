package com.echocyan.codenest.search.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.ArticleSearcher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基线实现：经 {@link ArticleApi#searchPublished} 用 {@code LIKE '%kw%'} 匹配标题、摘要、正文，每次查询都全表扫描。
 *
 * <p>没有分词，也就没有相关度：{@link SearchSort#RELEVANCE} 退化为和 {@link SearchSort#LATEST} 一样按发布时间倒序。
 * 不做高亮，高亮字段都为 null。
 */
@Service
@ConditionalOnProperty(name = "search.mode", havingValue = "mysql-like")
@RequiredArgsConstructor
class MysqlLikeArticleSearcher implements ArticleSearcher {

    private final ArticleApi articleApi;

    @Override
    public PageResult<Hit> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page,
                                  long size) {
        return articleApi.searchPublished(keyword, categoryId, tagId, page, size)
                .map(article -> new Hit(article, null, null));
    }
}
