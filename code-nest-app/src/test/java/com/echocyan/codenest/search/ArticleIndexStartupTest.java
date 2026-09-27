package com.echocyan.codenest.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.framework.lock.RedisLock;
import com.echocyan.codenest.search.service.ArticleIndex;
import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 启动时别名不存在的建索引。每个测试另建 {@link ArticleIndex}，用随机别名和桩化的 {@link ArticleApi}，
 * 与应用自己的 {@code article} 索引互不影响。
 */
class ArticleIndexStartupTest extends IntegrationTest {

    /**
     * 与 {@link ArticleIndex} 每批导入的文章数一致，桩返回满批时才会读下一批。
     */
    private static final int BATCH_SIZE = 500;

    private final String alias = "test_article_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private final ArticleApi articleApi = mock(ArticleApi.class);

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private RedisLock redisLock;

    private static List<ArticleSnapshot> snapshots(long fromId, long toId) {
        return LongStream.rangeClosed(fromId, toId)
                .mapToObj(id -> new ArticleSnapshot(id, 1L, 1L, "标题 " + id, "摘要", "正文", List.of(), List.of(),
                        ArticleStatus.PUBLISHED, LocalDateTime.now(), 1, false))
                .toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void deleteIndices() throws IOException {
        List<String> indices = List.copyOf(indices());
        if (!indices.isEmpty()) {
            client.indices().delete(delete -> delete.index(indices));
        }
    }

    @Test
    void importInterruptedAtStartupIsRedoneOnNextStartup() throws IOException {
        when(articleApi.listPublishedSnapshots(isNull(), anyInt())).thenReturn(snapshots(1, BATCH_SIZE));
        when(articleApi.listPublishedSnapshots(eq((long) BATCH_SIZE), anyInt()))
                .thenThrow(new IllegalStateException("数据库连接断开"))
                .thenReturn(snapshots(BATCH_SIZE + 1, BATCH_SIZE + 3));

        assertThatThrownBy(() -> newIndex().afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
        newIndex().afterSingletonsInstantiated();

        assertThat(countThroughAlias()).isEqualTo(BATCH_SIZE + 3);
        assertThat(indices()).hasSize(1);
    }

    @Test
    void instancesStartingTogetherBothStart() throws IOException {
        when(articleApi.listPublishedSnapshots(isNull(), anyInt())).thenReturn(snapshots(1, 3));
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> startups = List.of(newIndex(), newIndex()).stream()
                .map(index -> CompletableFuture.runAsync(() -> {
                    await(start);
                    index.afterSingletonsInstantiated();
                }))
                .toList();

        start.countDown();

        startups.forEach(CompletableFuture::join);
        assertThat(countThroughAlias()).isEqualTo(3);
        assertThat(indices()).hasSize(1);
    }

    private ArticleIndex newIndex() {
        return new ArticleIndex(client, articleApi, redisLock, alias);
    }

    private long countThroughAlias() throws IOException {
        client.indices().refresh(refresh -> refresh.index(alias));
        return client.count(count -> count.index(alias)).count();
    }

    private Set<String> indices() throws IOException {
        return client.indices().get(get -> get.index(alias + "_v*")).indices().keySet();
    }
}
