package com.echocyan.codenest.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.echocyan.codenest.article.ArticleTestSupport;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.search.service.ArticleIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * 各测试类共用一个数据库和一套索引，每个测试用一个随机关键词隔离数据。文章异步同步到索引，搜索结果用
 * {@link #eventually} 等待。零停机重建的测试经 {@link #onImportBatch} 在导入与切换别名之间插入操作。
 */
class SearchApiTest extends ArticleTestSupport {

    /**
     * 重建读完每一批待导入的文章后调用它，用来在导入与切换别名之间插入操作。
     */
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

    private static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static Map<String, Object> withTitle(String title) {
        return with("title", title);
    }

    private static Map<String, Object> withTitle(String title, int categoryId, int tagId) {
        Map<String, Object> body = draftIn(categoryId, tagId);
        body.put("title", title);
        return body;
    }

    private static Map<String, Object> with(String field, String value) {
        Map<String, Object> body = draft();
        body.put(field, value);
        return body;
    }

    @Test
    void matchesTitleSummaryOrContentOfPublishedArticlesOnly() {
        String keyword = uniqueKeyword();
        String username = uniqueUsername();
        RestTestClient author = withToken(register(username));
        String byTitle = publish(author, createDraft(author, withTitle("关于 " + keyword + " 的笔记")));
        String bySummary = publish(author, createDraft(author, with("summary", "摘要提到" + keyword)));
        String byContent = publish(author, createDraft(author, with("content", "正文里写着" + keyword + "。")));
        publish(author, createDraft(author, draft()));
        createDraft(author, withTitle(keyword + " 草稿"));
        delete(author, publish(author, createDraft(author, withTitle(keyword + " 已删除"))));

        eventually(() -> client.get().uri(API + "/search/articles?q={q}&sort=LATEST", keyword)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(byContent, bySummary, byTitle))
                .jsonPath("$.data.total").isEqualTo(3)
                .jsonPath("$.data.list[2].article.title").isEqualTo("关于 " + keyword + " 的笔记")
                .jsonPath("$.data.list[2].author.nickname").isEqualTo(username));
    }

    @Test
    void resultsFollowPublishEditAndDelete() {
        String before = uniqueKeyword();
        String after = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = createDraft(author, withTitle(before));
        publish(author, id);

        eventually(() -> expectHits(before, id));

        edit(author, id, 1, withTitle(after)).expectStatus().isOk();
        eventually(() -> {
            expectHits(before);
            expectHits(after, id);
        });

        delete(author, id);
        eventually(() -> expectHits(after));
    }

    @Test
    void resultsAreFilteredByCategoryAndTag() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String match = publish(author, createDraft(author, withTitle(keyword, 5, 7)));
        String otherTag = publish(author, createDraft(author, withTitle(keyword, 5, 8)));
        publish(author, createDraft(author, withTitle(keyword, 2, 7)));

        eventually(() -> {
            client.get().uri(API + "/search/articles?q={q}&categoryId=5&tagId=7", keyword)
                    .exchange()
                    .expectBody()
                    .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(match));
            client.get().uri(API + "/search/articles?q={q}&categoryId=5&sort=LATEST", keyword)
                    .exchange()
                    .expectBody()
                    .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(otherTag, match));
        });
    }

    @Test
    void resultsArePagedUpToFromPlusSizeOfOneThousand() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = publish(author, createDraft(author, withTitle(keyword)));
        String second = publish(author, createDraft(author, withTitle(keyword)));

        eventually(() -> client.get().uri(API + "/search/articles?q={q}&page=2&size=1", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(first))
                .jsonPath("$.data.total").isEqualTo(2)
                .jsonPath("$.data.page").isEqualTo(2)
                .jsonPath("$.data.size").isEqualTo(1));
        client.get().uri(API + "/search/articles?q={q}&page=50&size=20", keyword)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.list").isEmpty();
        for (String paging : List.of("page=51&size=20", "page=21&size=50")) {
            client.get().uri(API + "/search/articles?q={q}&" + paging, keyword)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(60001);
        }
        // 第一页不受影响
        client.get().uri(API + "/search/articles?q={q}&size=1", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(second));
    }

    @Test
    void parametersAreValidated() {
        for (String query : List.of("", "q=", "q=a&sort=HOT", "q=a&page=0", "q=a&size=0", "q=a&size=51")) {
            client.get().uri(API + "/search/articles?" + query)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
        client.get().uri(API + "/search/articles?q={q}", "  ")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }

    @Test
    void chineseKeywordsAreSegmented() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, withTitle(keyword + " 分布式事务的实现方案")));

        // 标题里没有连续的"事务方案"，分词后"事务""方案"都能命中
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

    /**
     * 断言按 LATEST 排序搜到的恰好是给定的文章。
     */
    private void expectHits(String keyword, String... articleIds) {
        client.get().uri(API + "/search/articles?q={q}&sort=LATEST", keyword)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(articleIds));
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
