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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 读取一页 Feed 的整体耗时记在 Timer {@code feed.read}，其中查询关注列表的耗时另记在 {@code feed.read.follow-list}，
 * 经管理端口的 metrics 端点查看；压测用它们判断关注列表要不要加缓存。
 */
@Service
class FeedServiceImpl implements FeedService {

    private final FollowService followService;
    private final FeedReader feedReader;
    private final UserApi userApi;
    private final CounterApi counterApi;
    private final Timer readTimer;
    private final Timer followListTimer;

    FeedServiceImpl(FollowService followService, FeedReader feedReader, UserApi userApi, CounterApi counterApi,
                    MeterRegistry meterRegistry) {
        this.followService = followService;
        this.feedReader = feedReader;
        this.userApi = userApi;
        this.counterApi = counterApi;
        this.readTimer = meterRegistry.timer("feed.read");
        this.followListTimer = meterRegistry.timer("feed.read.follow-list");
    }

    private static ArticleCountsVO countsVO(Counts counts) {
        return new ArticleCountsVO(
                counts.get(CounterMetric.ARTICLE_LIKE),
                counts.get(CounterMetric.ARTICLE_FAVORITE),
                counts.get(CounterMetric.ARTICLE_COMMENT),
                counts.get(CounterMetric.ARTICLE_VIEW));
    }

    @Override
    public CursorResult<FeedItemVO> read(long userId, Long cursor, int size) {
        return readTimer.record(() -> doRead(userId, cursor, size));
    }

    private CursorResult<FeedItemVO> doRead(long userId, Long cursor, int size) {
        List<Long> authorIds = followListTimer.record(() -> followService.listAllFollowedAuthorIds(userId));
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
