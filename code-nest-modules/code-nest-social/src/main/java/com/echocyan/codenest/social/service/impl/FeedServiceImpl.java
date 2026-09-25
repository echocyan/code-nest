package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.social.service.FeedReader;
import com.echocyan.codenest.social.service.FeedService;
import com.echocyan.codenest.social.service.FollowService;
import com.echocyan.codenest.social.vo.ArticleCountsVO;
import com.echocyan.codenest.social.vo.FeedItemVO;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class FeedServiceImpl implements FeedService {

    private final FollowService followService;
    private final FeedReader feedReader;
    private final UserApi userApi;
    private final CounterApi counterApi;

    private static ArticleCountsVO countsVO(Counts counts) {
        return new ArticleCountsVO(
                counts.get(CounterMetric.ARTICLE_LIKE),
                counts.get(CounterMetric.ARTICLE_FAVORITE),
                counts.get(CounterMetric.ARTICLE_COMMENT),
                counts.get(CounterMetric.ARTICLE_VIEW));
    }

    @Override
    public CursorResult<FeedItemVO> read(long userId, Long cursor, int size) {
        List<Long> authorIds = followService.listAllFollowedAuthorIds(userId);
        if (authorIds.isEmpty()) {
            return CursorResult.empty();
        }
        CursorResult<ArticleBrief> articles = feedReader.read(userId, authorIds, cursor, size);
        Map<Long, UserBrief> authors = userApi.getBriefs(
                articles.list().stream().map(ArticleBrief::authorId).distinct().toList());
        Map<Long, Counts> counts = counterApi.get(CounterTarget.ARTICLE,
                articles.list().stream().map(ArticleBrief::id).toList());
        return articles.map(article -> new FeedItemVO(
                article.id(),
                article.title(),
                article.summary(),
                article.coverUrl(),
                article.publishedAt(),
                authors.get(article.authorId()),
                countsVO(counts.get(article.id()))));
    }
}
