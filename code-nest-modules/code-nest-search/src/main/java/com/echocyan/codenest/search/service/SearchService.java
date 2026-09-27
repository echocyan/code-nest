package com.echocyan.codenest.search.service;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.SearchErrorCode;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章搜索：在 {@link ArticleIndex} 中检索，命中的文章经 {@link ArticleApi#listPublishedItems} 组装，同步尚未跟上的
 * 已删除文章不会出现在结果里，total 仍按 ES 计。
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    /**
     * 翻页上限：{@code from + size} 不超过它，避免深分页。
     */
    private static final long MAX_WINDOW = 1000;

    private final ArticleIndex articleIndex;
    private final ArticleApi articleApi;

    /**
     * 在已发布文章中按关键词搜索，页码分页。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     * @throws BizException {@link SearchErrorCode#PAGE_TOO_DEEP}
     */
    public PageResult<SearchArticleVO> search(String keyword, Long categoryId, Long tagId, SearchSort sort,
                                              long page, long size) {
        // from + size = page * size；用除法比较，page 极大时不会溢出
        if (page > MAX_WINDOW / size) {
            throw new BizException(SearchErrorCode.PAGE_TOO_DEEP);
        }
        PageResult<ArticleIndex.Match> hits = articleIndex.search(keyword.strip(), categoryId, tagId, sort, page, size);
        Map<Long, ArticleItem> items = articleApi.listPublishedItems(
                        hits.list().stream().map(ArticleIndex.Match::articleId).toList()).stream()
                .collect(Collectors.toMap(ArticleItem::id, Function.identity()));
        return new PageResult<>(hits.list().stream()
                .filter(hit -> items.containsKey(hit.articleId()))
                .map(hit -> new SearchArticleVO(items.get(hit.articleId()), hit.titleHighlight(),
                        hit.contentHighlight()))
                .toList(), hits.total(), page, size);
    }
}
