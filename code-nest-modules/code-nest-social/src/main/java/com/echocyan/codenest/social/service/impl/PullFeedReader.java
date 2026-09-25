package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.service.FeedReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基线实现：每次读取都经 {@link ArticleApi#listByAuthors} 对关注的全部作者做一次 {@code author_id IN (…)} 查询，
 * 关注的人越多越慢。
 */
@Service
@ConditionalOnProperty(name = "feed.mode", havingValue = "pull")
@RequiredArgsConstructor
class PullFeedReader implements FeedReader {

    private final ArticleApi articleApi;

    @Override
    public CursorResult<ArticleBrief> read(long userId, List<Long> authorIds, Long cursor, int size) {
        // 多查一条，只用来判断是否还有下一页
        List<ArticleBrief> fetched = articleApi.listByAuthors(authorIds, cursor, size + 1);
        boolean hasMore = fetched.size() > size;
        List<ArticleBrief> page = hasMore ? fetched.subList(0, size) : fetched;
        return new CursorResult<>(page, hasMore ? page.getLast().id() : null, hasMore);
    }
}
