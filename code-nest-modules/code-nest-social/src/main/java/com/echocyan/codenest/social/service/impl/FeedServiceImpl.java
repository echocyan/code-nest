package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.service.FeedService;
import com.echocyan.codenest.social.service.FeedStore;
import com.echocyan.codenest.social.service.FollowService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 从 {@link FeedStore} 取一页文章 ID，经 {@link ArticleApi#listPublishedItems} 组装并滤掉已删除和非发布状态的文章，
 * 再滤掉已取关作者的文章；过滤不影响翻页，所以一页可能不足 size 条。
 * <p>
 * 读取一页 Feed 的整体耗时记在 Timer {@code feed.read}，其中查询关注列表的耗时另记在 {@code feed.read.follow-list}，
 * 经管理端口的 metrics 端点查看；压测用它们判断关注列表要不要加缓存。
 */
@Service
class FeedServiceImpl implements FeedService {

    private final FollowService followService;
    private final FeedStore feedStore;
    private final ArticleApi articleApi;
    private final Timer readTimer;
    private final Timer followListTimer;

    FeedServiceImpl(FollowService followService, FeedStore feedStore, ArticleApi articleApi,
                    MeterRegistry meterRegistry) {
        this.followService = followService;
        this.feedStore = feedStore;
        this.articleApi = articleApi;
        this.readTimer = meterRegistry.timer("feed.read");
        this.followListTimer = meterRegistry.timer("feed.read.follow-list");
    }

    @Override
    public CursorResult<ArticleItem> read(long userId, Long cursor, int size) {
        return readTimer.record(() -> doRead(userId, cursor, size));
    }

    private CursorResult<ArticleItem> doRead(long userId, Long cursor, int size) {
        List<Long> authorIds = followListTimer.record(() -> followService.listAllFollowedAuthorIds(userId));
        if (authorIds.isEmpty()) {
            return CursorResult.empty();
        }
        CursorResult<Long> ids = feedStore.read(userId, authorIds, cursor, size);
        Set<Long> followed = Set.copyOf(authorIds);
        List<ArticleItem> items = articleApi.listPublishedItems(ids.list()).stream()
                .filter(item -> item.author() != null && followed.contains(item.author().id()))
                .toList();
        return new CursorResult<>(items, ids.nextCursor(), ids.hasMore());
    }
}
