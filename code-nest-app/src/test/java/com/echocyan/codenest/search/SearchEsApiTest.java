package com.echocyan.codenest.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.search.service.ArticleIndex;
import com.echocyan.codenest.support.EsSearch;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 在 es 档下运行 {@link SearchApiTest} 的全部测试，另外断言只有 es 档才有的相关度排序、标签加权、高亮、乱序写入，
 * 以及零停机重建。
 */
@EsSearch
class SearchEsApiTest extends SearchApiTest {

    /** 重建读完每一批待导入的文章后调用它，用来在导入与切换别名之间插入操作。 */
    private volatile Consumer<List<ArticleSnapshot>> onImportBatch = batch -> {
    };

    @MockitoSpyBean
    private ArticleApi articleApi;

    @Autowired
    private ArticleIndex articleIndex;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${local.management.port}")
    private int managementPort;

    @BeforeEach
    void hookImportBatches() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ArticleSnapshot> batch = (List<ArticleSnapshot>) invocation.callRealMethod();
            onImportBatch.accept(batch);
            return batch;
        }).when(articleApi).listPublishedSnapshots(any(), anyInt());
    }

    @Test
    void chineseKeywordsAreSegmented() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, withTitle(keyword + " 分布式事务的实现方案")));

        // 标题里没有连续的"事务方案"，按 LIKE 搜不到，分词后"事务""方案"都能命中
        eventually(() -> expectHits(keyword + " 事务方案", id));
    }

    @Test
    void titleMatchRanksAboveNewerContentMatch() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String byTitle = publish(author, createDraft(author, withTitle("关于 " + keyword + " 的笔记")));
        String byContent = publish(author, createDraft(author, with("content", "正文里写着" + keyword + "。")));

        eventually(() -> client.get().uri(API + "/search/articles?q={q}", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(byTitle, byContent)));
    }

    @Test
    void keywordEqualToATagNameBoostsArticlesWithThatTag() {
        RestTestClient author = withToken(register(uniqueUsername()));
        // 标签 24 = Kubernetes；不加权时标题命中的文章会排在前面
        Map<String, Object> tagged = withTitle("容器编排入门", 6, 24);
        tagged.put("content", "部署在 Kubernetes 上。");
        String taggedId = publish(author, createDraft(author, tagged));
        String untaggedId = publish(author, createDraft(author, withTitle("Kubernetes 入门", 6, 23)));

        eventually(() -> assertThat(searchIds("kubernetes"))
                .filteredOn(Set.of(taggedId, untaggedId)::contains)
                .containsExactly(taggedId, untaggedId));
    }

    @Test
    void titleAndAContentFragmentAreHighlighted() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        Map<String, Object> body = withTitle("关于 " + keyword + " 的笔记");
        body.put("content", "前".repeat(300) + keyword + "后".repeat(300));
        publish(author, createDraft(author, body));

        eventually(() -> client.get().uri(API + "/search/articles?q={q}", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].titleHighlight").isEqualTo("关于 <em>" + keyword + "</em> 的笔记")
                .jsonPath("$.data.list[0].contentHighlight").value(String.class, fragment -> assertThat(fragment)
                        .contains("<em>" + keyword + "</em>")
                        .hasSizeBetween(80, 130)));
    }

    /**
     * 两个消费者先后读到新旧两个版本、旧版本后写入的竞态从 HTTP 上构造不出来，这里直接用旧版本写入索引，再读 ES 里的文档。
     */
    @Test
    void staleWritesDoNotOverwriteNewerVersion() throws IOException {
        String before = uniqueKeyword();
        String after = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, withTitle(before)));
        edit(author, id, 1, withTitle(after)).expectStatus().isOk();
        eventually(() -> expectHits(after, id));

        ArticleSnapshot latest = articleApi.findSnapshot(Long.parseLong(id)).orElseThrow();
        ArticleSnapshot stale = new ArticleSnapshot(latest.id(), latest.authorId(), latest.categoryId(), before,
                latest.summary(), latest.content(), latest.tagIds(), latest.tagNames(), latest.status(),
                latest.publishedAt(), 1, false);
        articleIndex.save(stale);
        articleIndex.remove(latest.id(), 1);

        GetResponse<Map> indexed = elasticsearchClient.get(get -> get.index("article").id(id), Map.class);
        assertThat(indexed.found()).isTrue();
        assertThat(indexed.version()).isEqualTo(2);
        assertThat(indexed.source()).containsEntry("title", after);
    }

    @Test
    void rebuildKeepsResultsAndReplacesTheIndex() throws IOException {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        Map<String, Object> body = withTitle("关于 " + keyword + " 的笔记");
        body.put("content", "正文里写着" + keyword + "。");
        publish(author, createDraft(author, body));
        publish(author, createDraft(author, withTitle(keyword + " 入门")));
        eventually(() -> assertThat(searchIds(keyword)).hasSize(2));
        String before = searchBody(keyword);
        Set<String> oldIndices = aliasedIndices();

        rebuild().expectStatus().isOk();

        assertThat(searchBody(keyword)).isEqualTo(before);
        Set<String> newIndices = aliasedIndices();
        assertThat(newIndices).hasSize(1).doesNotContainAnyElementsOf(oldIndices);
        assertThat(elasticsearchClient.indices().exists(exists -> exists.index(List.copyOf(oldIndices))).value())
                .isFalse();
    }

    /**
     * 在导入读完测试文章之后、切换别名之前编辑和删除文章，并等同步写入旧索引：新索引导入的是旧版本，
     * 只有按 updated_at 追补才能在重建后搜到最新内容。
     */
    @Test
    void changesDuringRebuildAreCaughtUp() {
        String original = uniqueKeyword();
        String edited = uniqueKeyword();
        String removedKeyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, withTitle(original)));
        String removed = publish(author, createDraft(author, withTitle(removedKeyword)));
        eventually(() -> {
            expectHits(original, id);
            expectHits(removedKeyword, removed);
        });
        AtomicBoolean changed = new AtomicBoolean();
        onImportBatch = batch -> {
            if (batch.stream().anyMatch(snapshot -> snapshot.id().toString().equals(removed))
                    && changed.compareAndSet(false, true)) {
                edit(author, id, 1, withTitle(edited)).expectStatus().isOk();
                delete(author, removed);
                eventually(() -> {
                    expectHits(edited, id);
                    expectHits(removedKeyword);
                });
            }
        };

        rebuild().expectStatus().isOk();

        assertThat(changed).isTrue();
        eventually(() -> {
            expectHits(original);
            expectHits(edited, id);
            expectHits(removedKeyword);
        });
    }

    @Test
    void searchKeepsWorkingDuringRebuild() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, withTitle(keyword)));
        eventually(() -> expectHits(keyword, id));
        AtomicBoolean rebuilding = new AtomicBoolean(true);
        AtomicInteger searches = new AtomicInteger();
        CompletableFuture<Void> searching = CompletableFuture.runAsync(() -> {
            while (rebuilding.get()) {
                expectHits(keyword, id);
                searches.incrementAndGet();
            }
        });

        try {
            rebuild().expectStatus().isOk();
        } finally {
            rebuilding.set(false);
        }

        searching.join();
        assertThat(searches).hasPositiveValue();
    }

    @Test
    void onlyOneRebuildRunsAtATime() {
        AtomicBoolean checked = new AtomicBoolean();
        onImportBatch = batch -> {
            if (checked.compareAndSet(false, true)) {
                rebuild().expectStatus().isEqualTo(409);
            }
        };

        rebuild().expectStatus().isOk();

        assertThat(checked).isTrue();
    }

    private RestTestClient.ResponseSpec rebuild() {
        return RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build()
                .post().uri("/actuator/search-rebuild")
                .exchange();
    }

    private String searchBody(String keyword) {
        return client.get().uri(API + "/search/articles?q={q}", keyword)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody();
    }

    private Set<String> aliasedIndices() throws IOException {
        return elasticsearchClient.indices().getAlias(alias -> alias.name(ArticleIndex.ALIAS)).aliases().keySet();
    }

    private List<String> searchIds(String keyword) {
        AtomicReference<List<String>> ids = new AtomicReference<>();
        client.get().uri(API + "/search/articles?q={q}&size=50", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").value(List.class, list -> ids.set(list));
        return ids.get();
    }
}
