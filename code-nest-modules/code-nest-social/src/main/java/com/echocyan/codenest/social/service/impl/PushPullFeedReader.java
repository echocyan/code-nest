package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.service.FeedReader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 优化实现：普通作者的文章由 {@link FeedFanoutServiceImpl} 推送到读者的收件箱，大 V 的文章读取时从其发件箱拉取，
 * 两路合并后按文章 ID 去重、倒序截取一页。收件箱不存在（读者 7 天没来过）时先从普通作者的发件箱重建。
 *
 * <p>收件箱里可能残留已删除的文章、已取关作者的文章，截取后再过滤，所以一页可能不足 size 条。
 * 收件箱至多 {@value FeedBoxes#INBOX_CAP} 条、发件箱至多 {@value FeedBoxes#OUTBOX_CAP} 条，Feed 只能往回翻这么多。
 */
@Service
@ConditionalOnProperty(name = "feed.mode", havingValue = "push-pull")
@RequiredArgsConstructor
class PushPullFeedReader implements FeedReader {

    private final FeedBoxes feedBoxes;
    private final BigAuthors bigAuthors;
    private final ArticleApi articleApi;

    @Override
    public CursorResult<ArticleBrief> read(long userId, List<Long> authorIds, Long cursor, int size) {
        Set<Long> big = bigAuthors.among(authorIds);
        feedBoxes.renewOrRebuildInbox(userId, authorIds.stream().filter(id -> !big.contains(id)).toList());
        // 多取一条，只用来判断是否还有下一页
        List<Long> ids = feedBoxes.readBefore(userId, big, cursor, size + 1).stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(size + 1)
                .toList();
        boolean hasMore = ids.size() > size;
        List<Long> pageIds = hasMore ? ids.subList(0, size) : ids;
        Set<Long> followed = Set.copyOf(authorIds);
        Map<Long, ArticleBrief> briefs = articleApi.getBriefs(pageIds);
        List<ArticleBrief> page = pageIds.stream()
                .map(briefs::get)
                .filter(Objects::nonNull)
                .filter(brief -> brief.status() == ArticleStatus.PUBLISHED && followed.contains(brief.authorId()))
                .toList();
        return new CursorResult<>(page, hasMore ? pageIds.getLast() : null, hasMore);
    }
}
