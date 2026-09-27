package com.echocyan.codenest.social;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.social.service.FeedStore;
import com.echocyan.codenest.social.service.FollowerGraph;
import com.echocyan.codenest.support.IntegrationTest;
import com.echocyan.codenest.user.api.UserBrief;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feed 存储的推送、拉取、修正、过滤与重建。每个测试另建 {@link FeedStore}，用随机的 key 前缀、内存里的关注关系和
 * 桩化的文章，大 V 阈值为 2 个粉丝。
 */
class FeedStoreTest extends IntegrationTest {

    private static final long READER = 1;
    private static final long FAN = 2;
    private static final long NORMAL = 101;
    private static final long OTHER_NORMAL = 102;
    private static final long BIG = 103;

    private final InMemoryFollowerGraph graph = new InMemoryFollowerGraph();

    private final ArticleApi articleApi = mock(ArticleApi.class);

    /**
     * 已发布、未删除的文章及其作者。
     */
    private final Map<Long, Long> published = new HashMap<>();

    @Autowired
    private StringRedisTemplate redis;

    private FeedStore store;

    private static ArticleState state(long id, long authorId) {
        return new ArticleState(id, authorId, ArticleStatus.PUBLISHED);
    }

    private static ArticleItem item(long id, long authorId) {
        return new ArticleItem(id, "标题", "摘要", null, null, ArticleStatus.PUBLISHED, null,
                new UserBrief(authorId, "作者", null), null);
    }

    @BeforeEach
    void createStore() {
        store = new FeedStore(redis, graph, articleApi, new SimpleMeterRegistry(), "test:feed:" + UUID.randomUUID(), 2);
        when(articleApi.listPublishedItems(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream().filter(published::containsKey).map(id -> item(id, published.get(id))).toList();
        });
    }

    private void follow(long followerId, long authorId) {
        graph.follow(followerId, authorId);
        store.follow(followerId, authorId);
    }

    private void unfollow(long followerId, long authorId) {
        graph.unfollow(followerId, authorId);
        store.unfollow(followerId, authorId);
    }

    private void givenFollowers(long authorId, long... followerIds) {
        for (long followerId : followerIds) {
            follow(followerId, authorId);
        }
    }

    private void publish(long articleId, long authorId) {
        published.put(articleId, authorId);
        store.addArticle(articleId, authorId);
    }

    @Test
    void articlesOfNormalAuthorsArePushedAndThoseOfBigAuthorsArePulled() {
        givenFollowers(NORMAL, READER);
        givenFollowers(BIG, READER, FAN);
        publish(1, NORMAL);
        // 读者还没有收件箱，推送跳过了他；首次读取时从普通作者的发件箱重建
        assertThat(readAll(READER, 20)).containsExactly(1L);

        publish(2, BIG);
        publish(3, NORMAL);
        publish(4, BIG);

        assertThat(readAll(READER, 2)).containsExactly(4L, 3L, 2L, 1L);
        assertThat(graph.pagedAuthors).doesNotContain(BIG);
    }

    /**
     * 作者当大 V 期间的文章没有推送；降为普通作者后不再拉取，这些文章仍要出现在粉丝的 Feed 里。
     */
    @Test
    void articlesOfAnAuthorNoLongerBigStayInTheFeed() {
        givenFollowers(NORMAL, READER);
        givenFollowers(BIG, READER, FAN);
        publish(1, NORMAL);
        assertThat(readAll(READER, 20)).containsExactly(1L);
        publish(2, BIG);

        unfollow(FAN, BIG);

        assertThat(readAll(READER, 20)).containsExactly(2L, 1L);
    }

    @Test
    void followMergesRecentArticlesOfTheAuthorIntoTheInbox() {
        givenFollowers(NORMAL, READER);
        publish(1, NORMAL);
        assertThat(readAll(READER, 20)).containsExactly(1L);
        publish(2, OTHER_NORMAL);
        publish(3, OTHER_NORMAL);

        givenFollowers(OTHER_NORMAL, READER);

        assertThat(readAll(READER, 20)).containsExactly(3L, 2L, 1L);
    }

    @Test
    void unfollowRemovesArticlesOfTheAuthorFromTheInbox() {
        givenFollowers(NORMAL, READER);
        givenFollowers(OTHER_NORMAL, READER);
        publish(1, NORMAL);
        publish(2, OTHER_NORMAL);
        assertThat(readAll(READER, 20)).containsExactly(2L, 1L);

        unfollow(READER, OTHER_NORMAL);

        assertThat(readAll(READER, 20)).containsExactly(1L);
    }

    @Test
    void removedArticleLeavesTheOutbox() {
        givenFollowers(BIG, READER, FAN);
        publish(1, BIG);
        publish(2, BIG);

        store.removeArticle(2, BIG);

        assertThat(readAll(READER, 20)).containsExactly(1L);
    }

    /**
     * 收件箱不会随删文、取关逐个清理，读取时按文章的最新状态和当前的关注关系过滤；取关事件还没处理时也一样。
     */
    @Test
    void deletedArticlesAndUnfollowedAuthorsAreFilteredOut() {
        givenFollowers(NORMAL, READER);
        givenFollowers(OTHER_NORMAL, READER);
        publish(1, NORMAL);
        publish(2, NORMAL);
        publish(3, OTHER_NORMAL);
        assertThat(readAll(READER, 20)).containsExactly(3L, 2L, 1L);

        published.remove(2L);
        graph.unfollow(READER, OTHER_NORMAL);

        assertThat(readAll(READER, 20)).containsExactly(1L);
    }

    /**
     * 发件箱与粉丝数都只在启动时按数据库重建；第一次启动导入到一半失败，第二次启动补全。
     */
    @Test
    void lostOutboxesAndFollowerCountsAreRebuiltAtStartup() {
        when(articleApi.listPublishedStates(isNull(), anyInt()))
                .thenThrow(new IllegalStateException("数据库连接断开"))
                .thenReturn(List.of(state(1, NORMAL), state(2, BIG)));
        published.put(1L, NORMAL);
        published.put(2L, BIG);
        // 只写关注表，不经 store，Redis 里还没有粉丝数
        graph.follow(READER, NORMAL);
        graph.follow(READER, BIG);
        graph.follow(FAN, BIG);

        assertThatThrownBy(store::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
        store.afterSingletonsInstantiated();

        assertThat(readAll(READER, 20)).containsExactly(2L, 1L);
        publish(3, BIG);
        assertThat(readAll(READER, 20)).containsExactly(3L, 2L, 1L);
        assertThat(graph.pagedAuthors).doesNotContain(BIG);
    }

    /**
     * 大于 2^53 的相邻雪花 ID 转成 ZSet 的 double score 后相同，翻页时按 ID 精确切分。
     */
    @Test
    void pagesNeitherRepeatNorSkipArticlesWithTheSameScore() {
        givenFollowers(NORMAL, READER);
        List<Long> ids = LongStream.rangeClosed(1, 7).map(i -> (1L << 60) + i).boxed().toList();
        ids.forEach(id -> publish(id, NORMAL));

        assertThat(readAll(READER, 3)).containsExactlyElementsOf(ids.reversed());
    }

    private List<Long> readAll(long userId, int size) {
        List<Long> ids = new ArrayList<>();
        Long cursor = null;
        CursorResult<ArticleItem> page;
        do {
            page = store.read(userId, cursor, size);
            page.list().forEach(item -> ids.add(item.id()));
            cursor = page.nextCursor();
        } while (page.hasMore());
        return ids;
    }

    /**
     * 内存里的关注表。另记下被遍历过粉丝的作者，用来断言大 V 发文没有写扩散。
     */
    private static class InMemoryFollowerGraph implements FollowerGraph {

        private final Map<Long, SortedSet<Long>> followersByAuthor = new HashMap<>();

        private final Set<Long> pagedAuthors = new HashSet<>();

        void follow(long followerId, long authorId) {
            followersByAuthor.computeIfAbsent(authorId, id -> new TreeSet<>()).add(followerId);
        }

        void unfollow(long followerId, long authorId) {
            followersByAuthor.getOrDefault(authorId, new TreeSet<>()).remove(followerId);
        }

        @Override
        public List<Long> listAllFollowedAuthorIds(long followerId) {
            return followersByAuthor.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(followerId))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        @Override
        public List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit) {
            pagedAuthors.add(authorId);
            SortedSet<Long> followers = followersByAuthor.getOrDefault(authorId, new TreeSet<>());
            return (afterFollowerId == null ? followers : followers.tailSet(afterFollowerId + 1)).stream()
                    .limit(limit)
                    .toList();
        }

        @Override
        public long countFollowers(long authorId) {
            return followersByAuthor.getOrDefault(authorId, new TreeSet<>()).size();
        }

        @Override
        public List<IdCount> countFollowersAfter(long afterAuthorId, int limit) {
            return followersByAuthor.entrySet().stream()
                    .filter(entry -> entry.getKey() > afterAuthorId && !entry.getValue().isEmpty())
                    .sorted(Map.Entry.comparingByKey())
                    .limit(limit)
                    .map(entry -> new IdCount(entry.getKey(), entry.getValue().size()))
                    .toList();
        }
    }
}
