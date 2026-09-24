package com.echocyan.codenest.search.service;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.SearchErrorCode;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.vo.SearchArticleVO;

/**
 * 文章搜索。
 */
public interface SearchService {

    /**
     * 在已发布文章中按关键词搜索，页码分页。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     * @throws BizException {@link SearchErrorCode#PAGE_TOO_DEEP}
     */
    PageResult<SearchArticleVO> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page,
                                       long size);
}
