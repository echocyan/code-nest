package com.echocyan.codenest.search.service.impl;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.SearchErrorCode;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.ArticleSearcher;
import com.echocyan.codenest.search.service.SearchService;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class SearchServiceImpl implements SearchService {

    /** 翻页上限：{@code from + size} 不超过它，避免深分页。 */
    private static final long MAX_WINDOW = 1000;

    private final ArticleSearcher articleSearcher;
    private final UserApi userApi;

    @Override
    public PageResult<SearchArticleVO> search(String keyword, Long categoryId, Long tagId, SearchSort sort,
                                              long page, long size) {
        // from + size = page * size；用除法比较，page 极大时不会溢出
        if (page > MAX_WINDOW / size) {
            throw new BizException(SearchErrorCode.PAGE_TOO_DEEP);
        }
        PageResult<ArticleSearcher.Hit> hits = articleSearcher.search(keyword.strip(), categoryId, tagId, sort, page,
                size);
        Map<Long, UserBrief> authors = userApi.getBriefs(
                hits.list().stream().map(hit -> hit.article().authorId()).distinct().toList());
        return hits.map(hit -> new SearchArticleVO(hit.article(), authors.get(hit.article().authorId()),
                hit.titleHighlight(), hit.contentHighlight()));
    }
}
