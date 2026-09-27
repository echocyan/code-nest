package com.echocyan.codenest.search.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.SearchErrorCode;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.SearchService;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 命中的文章经 {@link ArticleApi#listPublishedItems} 组装，同步尚未跟上的已删除文章不会出现在结果里，total 仍按 ES 计。
 */
@Service
@RequiredArgsConstructor
class SearchServiceImpl implements SearchService {

    /**
     * 翻页上限：{@code from + size} 不超过它，避免深分页。
     */
    private static final long MAX_WINDOW = 1000;

    private final EsArticleSearcher articleSearcher;
    private final ArticleApi articleApi;

    @Override
    public PageResult<SearchArticleVO> search(String keyword, Long categoryId, Long tagId, SearchSort sort,
                                              long page, long size) {
        // from + size = page * size；用除法比较，page 极大时不会溢出
        if (page > MAX_WINDOW / size) {
            throw new BizException(SearchErrorCode.PAGE_TOO_DEEP);
        }
        PageResult<EsArticleSearcher.Match> hits =
                articleSearcher.search(keyword.strip(), categoryId, tagId, sort, page,
                        size);
        Map<Long, ArticleItem> items = articleApi.listPublishedItems(
                        hits.list().stream().map(EsArticleSearcher.Match::articleId).toList()).stream()
                .collect(Collectors.toMap(ArticleItem::id, Function.identity()));
        return new PageResult<>(hits.list().stream()
                .filter(hit -> items.containsKey(hit.articleId()))
                .map(hit -> new SearchArticleVO(items.get(hit.articleId()), hit.titleHighlight(),
                        hit.contentHighlight()))
                .toList(), hits.total(), page, size);
    }
}
