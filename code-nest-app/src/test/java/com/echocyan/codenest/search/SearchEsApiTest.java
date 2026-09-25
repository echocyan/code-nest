package com.echocyan.codenest.search;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 在 es 档下运行 {@link SearchApiTest} 的全部测试，另外断言只有 es 档才有的相关度排序、标签加权、高亮，以及乱序写入。
 */
@EsSearch
class SearchEsApiTest extends SearchApiTest {

    @Autowired
    private ArticleApi articleApi;

    @Autowired
    private ArticleIndex articleIndex;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

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

    private List<String> searchIds(String keyword) {
        AtomicReference<List<String>> ids = new AtomicReference<>();
        client.get().uri(API + "/search/articles?q={q}&size=50", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").value(List.class, list -> ids.set(list));
        return ids.get();
    }
}
