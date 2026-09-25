package com.echocyan.codenest.article;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 各测试类共用一个数据库，每个测试用一个其他测试不用的标签隔离数据。本类在每个缓存档下各跑一遍，
 * 同一标签下可能已有另一档写入的文章，所以按发布时间倒序只断言开头的几篇，总数以测试前为基准。
 */
class ArticleListApiTest extends ArticleTestSupport {

    @Test
    void latestArticlesAreFilteredByCategoryAndTagNewestFirst() {
        long before = totalOf("/articles?categoryId=5&tagId=28");
        RestTestClient author = withToken(register(uniqueUsername()));
        String older = publish(author, createDraft(author, draftIn(5, 28)));
        createDraft(author, draftIn(5, 28));
        String newer = publish(author, createDraft(author, draftIn(5, 28)));
        String otherCategory = publish(author, createDraft(author, draftIn(2, 28)));
        delete(author, publish(author, createDraft(author, draftIn(5, 28))));

        client.get().uri(API + "/articles?categoryId=5&tagId=28")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[0:2].id").isEqualTo(List.of(newer, older))
                .jsonPath("$.data.total").isEqualTo(before + 2);
        client.get().uri(API + "/articles?tagId=28")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0:3].id").isEqualTo(List.of(otherCategory, newer, older));
    }

    @Test
    void latestArticlesArePagedByPageNumber() {
        long total = totalOf("/articles?tagId=27") + 3;
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = publish(author, createDraft(author, draftIn(1, 27)));
        String second = publish(author, createDraft(author, draftIn(1, 27)));
        String third = publish(author, createDraft(author, draftIn(1, 27)));

        client.get().uri(API + "/articles?tagId=27&page=1&size=2")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(third, second))
                .jsonPath("$.data.total").isEqualTo(total)
                .jsonPath("$.data.page").isEqualTo(1)
                .jsonPath("$.data.size").isEqualTo(2);
        client.get().uri(API + "/articles?tagId=27&page=2&size=2")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].id").isEqualTo(first);
        long pastLastPage = (total + 1) / 2 + 1;
        client.get().uri(API + "/articles?tagId=27&page={page}&size=2", pastLastPage)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list").isEmpty()
                .jsonPath("$.data.total").isEqualTo(total);
    }

    private long totalOf(String uri) {
        AtomicReference<Long> total = new AtomicReference<>();
        client.get().uri(API + uri)
                .exchange()
                .expectBody().jsonPath("$.data.total").value(Long.class, total::set);
        return total.get();
    }

    @Test
    void listItemCarriesAuthorAndCounts() {
        String username = uniqueUsername();
        RestTestClient author = withToken(register(username));
        String id = publish(author, createDraft(author, draftIn(1, 26)));
        client.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        client.get().uri(API + "/articles?tagId=26")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].title").isEqualTo("Redis 计数实践")
                .jsonPath("$.data.list[0].summary").isEqualTo("手写摘要")
                .jsonPath("$.data.list[0].category.name").isEqualTo("后端")
                .jsonPath("$.data.list[0].status").isEqualTo("PUBLISHED")
                .jsonPath("$.data.list[0].author.nickname").isEqualTo(username)
                .jsonPath("$.data.list[0].counts.viewCount").isEqualTo(1)
                .jsonPath("$.data.list[0].content").doesNotExist();
    }

    @Test
    void authorsPublishedArticlesArePagedByCursor() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = publish(author, createDraft(author, draft()));
        createDraft(author, draft());
        String second = publish(author, createDraft(author, draft()));
        delete(author, publish(author, createDraft(author, draft())));
        String third = publish(author, createDraft(author, draft()));
        String authorId = authorIdOf(author, first);

        AtomicReference<String> cursor = new AtomicReference<>();
        client.get().uri(API + "/users/{id}/articles?size=2", authorId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(third, second))
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);
        client.get().uri(API + "/users/{id}/articles?size=2&cursor={cursor}", authorId, cursor.get())
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(first))
                .jsonPath("$.data.hasMore").isEqualTo(false)
                .jsonPath("$.data.nextCursor").isEmpty();
    }

    @Test
    void authorSeesOnlyTheirOwnDrafts() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = createDraft(author, draft());
        publish(author, createDraft(author, draft()));
        String second = createDraft(author, draft());
        delete(author, createDraft(author, draft()));
        RestTestClient other = withToken(register(uniqueUsername()));
        createDraft(other, draft());

        author.get().uri(API + "/users/me/drafts")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(second, first))
                .jsonPath("$.data.list[*].status").isEqualTo(List.of("DRAFT", "DRAFT"))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void draftsRequireLogin() {
        client.get().uri(API + "/users/me/drafts")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void pagingParametersAreValidated() {
        for (String query : List.of("size=0", "size=51", "page=0", "size=abc")) {
            client.get().uri(API + "/articles?" + query)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
    }
}
