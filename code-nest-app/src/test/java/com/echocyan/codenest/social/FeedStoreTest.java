package com.echocyan.codenest.social;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.social.service.FeedStore;
import com.echocyan.codenest.social.service.FollowService;
import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feed 存储的推送、拉取、修正与重建。每个测试另建 {@link FeedStore}，用随机的 key 前缀和桩化的关注关系、文章，
 * 大 V 阈值为 2 个粉丝。
 */
class FeedStoreTest extends IntegrationTest {

    private static final long READER = 1;
    private static final long FAN = 2;
    private static final long NORMAL = 101;
    private static final long OTHER_NORMAL = 102;
    private static final long BIG = 103;

    private final FollowService followService = mock(FollowService.class);

    private final ArticleApi articleApi = mock(ArticleApi.class);

    @Autowired
    private StringRedisTemplate redis;

    private FeedStore store;

    private static ArticleState state(long id, long authorId) {
        return new ArticleState(id, authorId, ArticleStatus.PUBLISHED);
    }

    @BeforeEach
    void createStore() {
        store = new FeedStore(redis, followService, articleApi, "test:feed:" + UUID.randomUUID(), 2);
    }

    /**
     * 让作者有给定的粉丝，并经 {@link FeedStore#follow} 记下粉丝数。
     */
    private void givenFollowers(long authorId, Long... followerIds) {
        when(followService.countFollowers(authorId)).thenReturn((long) followerIds.length);
        when(followService.listFollowerIds(eq(authorId), isNull(), anyInt())).thenReturn(List.of(followerIds));
        for (long followerId : followerIds) {
            store.follow(followerId, authorId);
        }
    }

    @Test
    void articlesOfNormalAuthorsArePushedAndThoseOfBigAuthorsArePulled() {
        givenFollowers(NORMAL, READER);
        givenFollowers(BIG, READER, FAN);
        store.addArticle(1, NORMAL);
        // 读者还没有收件箱，推送跳过了他；首次读取时从普通作者的发件箱重建
        assertThat(readAll(READER, List.of(NORMAL, BIG), 20)).containsExactly(1L);

        store.addArticle(2, BIG);
        store.addArticle(3, NORMAL);
        store.addArticle(4, BIG);

        assertThat(readAll(READER, List.of(NORMAL, BIG), 2)).containsExactly(4L, 3L, 2L, 1L);
        verify(followService, never()).listFollowerIds(eq(BIG), any(), anyInt());
    }

    /**
     * 作者当大 V 期间的文章没有推送；降为普通作者后不再拉取，这些文章仍要出现在粉丝的 Feed 里。
     */
    @Test
    void articlesOfAnAuthorNoLongerBigStayInTheFeed() {
        givenFollowers(NORMAL, READER);
        givenFollowers(BIG, READER, FAN);
        store.addArticle(1, NORMAL);
        assertThat(readAll(READER, List.of(NORMAL, BIG), 20)).containsExactly(1L);
        store.addArticle(2, BIG);

        when(followService.countFollowers(BIG)).thenReturn(1L);
        when(followService.listFollowerIds(eq(BIG), isNull(), anyInt())).thenReturn(List.of(READER));
        store.unfollow(FAN, BIG);

        assertThat(readAll(READER, List.of(NORMAL, BIG), 20)).containsExactly(2L, 1L);
    }

    @Test
    void followMergesRecentArticlesOfTheAuthorIntoTheInbox() {
        givenFollowers(NORMAL, READER);
        store.addArticle(1, NORMAL);
        assertThat(readAll(READER, List.of(NORMAL), 20)).containsExactly(1L);
        store.addArticle(2, OTHER_NORMAL);
        store.addArticle(3, OTHER_NORMAL);

        givenFollowers(OTHER_NORMAL, READER);

        assertThat(readAll(READER, List.of(NORMAL, OTHER_NORMAL), 20)).containsExactly(3L, 2L, 1L);
    }

    @Test
    void unfollowRemovesArticlesOfTheAuthorFromTheInbox() {
        givenFollowers(NORMAL, READER);
        givenFollowers(OTHER_NORMAL, READER);
        store.addArticle(1, NORMAL);
        store.addArticle(2, OTHER_NORMAL);
        assertThat(readAll(READER, List.of(NORMAL, OTHER_NORMAL), 20)).containsExactly(2L, 1L);

        when(followService.countFollowers(OTHER_NORMAL)).thenReturn(0L);
        store.unfollow(READER, OTHER_NORMAL);

        assertThat(readAll(READER, List.of(NORMAL), 20)).containsExactly(1L);
    }

    @Test
    void removedArticleLeavesTheOutbox() {
        givenFollowers(BIG, READER, FAN);
        store.addArticle(1, BIG);
        store.addArticle(2, BIG);

        store.removeArticle(2, BIG);

        assertThat(readAll(READER, List.of(BIG), 20)).containsExactly(1L);
    }

    /**
     * 发件箱与粉丝数都只在启动时按数据库重建；第一次启动导入到一半失败，第二次启动补全。
     */
    @Test
    void lostOutboxesAndFollowerCountsAreRebuiltAtStartup() {
        when(articleApi.listPublishedStates(isNull(), anyInt()))
                .thenThrow(new IllegalStateException("数据库连接断开"))
                .thenReturn(List.of(state(1, NORMAL), state(2, BIG)));
        when(followService.countFollowersAfter(eq(0L), anyInt()))
                .thenReturn(List.of(new IdCount(NORMAL, 1), new IdCount(BIG, 2)));
        when(followService.listFollowerIds(eq(NORMAL), isNull(), anyInt())).thenReturn(List.of(READER));

        assertThatThrownBy(store::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
        store.afterSingletonsInstantiated();

        assertThat(readAll(READER, List.of(NORMAL, BIG), 20)).containsExactly(2L, 1L);
        store.addArticle(3, BIG);
        assertThat(readAll(READER, List.of(NORMAL, BIG), 20)).containsExactly(3L, 2L, 1L);
        verify(followService, never()).listFollowerIds(eq(BIG), any(), anyInt());
    }

    /**
     * 大于 2^53 的相邻雪花 ID 转成 ZSet 的 double score 后相同，翻页时按 ID 精确切分。
     */
    @Test
    void pagesNeitherRepeatNorSkipArticlesWithTheSameScore() {
        givenFollowers(NORMAL, READER);
        List<Long> ids = LongStream.rangeClosed(1, 7).map(i -> (1L << 60) + i).boxed().toList();
        ids.forEach(id -> store.addArticle(id, NORMAL));

        assertThat(readAll(READER, List.of(NORMAL), 3)).containsExactlyElementsOf(ids.reversed());
    }

    private List<Long> readAll(long userId, List<Long> authorIds, int size) {
        List<Long> ids = new ArrayList<>();
        Long cursor = null;
        CursorResult<Long> page;
        do {
            page = store.read(userId, authorIds, cursor, size);
            ids.addAll(page.list());
            cursor = page.nextCursor();
        } while (page.hasMore());
        return ids;
    }
}
