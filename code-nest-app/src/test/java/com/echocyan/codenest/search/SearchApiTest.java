package com.echocyan.codenest.search;

import com.echocyan.codenest.article.ArticleTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 各测试类共用一个数据库，每个测试用一个随机关键词隔离数据。es 档下文章异步同步到索引，搜索结果用
 * {@link #eventually} 等待；两档要求搜到同一批文章，需要确定顺序时按 LATEST 排序。
 */
class SearchApiTest extends ArticleTestSupport {

    protected static String uniqueKeyword() {
        return "kw" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected static Map<String, Object> withTitle(String title) {
        return with("title", title);
    }

    protected static Map<String, Object> withTitle(String title, int categoryId, int tagId) {
        Map<String, Object> body = draftIn(categoryId, tagId);
        body.put("title", title);
        return body;
    }

    protected static Map<String, Object> with(String field, String value) {
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
    void likeWildcardsInKeywordMatchLiterally() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String literal = publish(author, createDraft(author, withTitle("100%_" + keyword)));
        publish(author, createDraft(author, withTitle("100ab" + keyword)));

        eventually(() -> expectHits("%_" + keyword, literal));
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

    /**
     * 断言按 LATEST 排序搜到的恰好是给定的文章。
     */
    protected void expectHits(String keyword, String... articleIds) {
        client.get().uri(API + "/search/articles?q={q}&sort=LATEST", keyword)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(articleIds));
    }
}
