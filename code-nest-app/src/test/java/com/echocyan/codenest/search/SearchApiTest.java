package com.echocyan.codenest.search;

import com.echocyan.codenest.article.ArticleTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 各测试类共用一个数据库，每个测试用一个随机关键词隔离数据。
 */
class SearchApiTest extends ArticleTestSupport {

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

        client.get().uri(API + "/search/articles?q={q}", keyword)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(byContent, bySummary, byTitle))
                .jsonPath("$.data.total").isEqualTo(3)
                .jsonPath("$.data.list[2].article.title").isEqualTo("关于 " + keyword + " 的笔记")
                .jsonPath("$.data.list[2].author.nickname").isEqualTo(username)
                .jsonPath("$.data.list[2].titleHighlight").isEmpty()
                .jsonPath("$.data.list[2].contentHighlight").isEmpty();
    }

    @Test
    void resultsAreFilteredByCategoryAndTag() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String match = publish(author, createDraft(author, withTitle(keyword, 5, 7)));
        String otherTag = publish(author, createDraft(author, withTitle(keyword, 5, 8)));
        publish(author, createDraft(author, withTitle(keyword, 2, 7)));

        client.get().uri(API + "/search/articles?q={q}&categoryId=5&tagId=7", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(match));
        client.get().uri(API + "/search/articles?q={q}&categoryId=5&sort=LATEST", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(otherTag, match));
    }

    @Test
    void likeWildcardsInKeywordMatchLiterally() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String literal = publish(author, createDraft(author, withTitle("100%_" + keyword)));
        publish(author, createDraft(author, withTitle("100ab" + keyword)));

        client.get().uri(API + "/search/articles?q={q}", "%_" + keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(literal));
    }

    @Test
    void resultsArePagedUpToFromPlusSizeOfOneThousand() {
        String keyword = uniqueKeyword();
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = publish(author, createDraft(author, withTitle(keyword)));
        String second = publish(author, createDraft(author, withTitle(keyword)));

        client.get().uri(API + "/search/articles?q={q}&page=2&size=1", keyword)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(first))
                .jsonPath("$.data.total").isEqualTo(2)
                .jsonPath("$.data.page").isEqualTo(2)
                .jsonPath("$.data.size").isEqualTo(1);
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
}
