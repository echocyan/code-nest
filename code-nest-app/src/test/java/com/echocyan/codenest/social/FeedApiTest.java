package com.echocyan.codenest.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.article.ArticleTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.RestTestClient;

class FeedApiTest extends ArticleTestSupport {

    @Test
    void feedShowsPublishedArticlesOfFollowedAuthorsOnly() {
        String authorName = uniqueUsername();
        RestTestClient author = withToken(register(authorName));
        String published = publish(author, createDraft(author, draft()));
        createDraft(author, draft());
        delete(author, publish(author, createDraft(author, draft())));
        RestTestClient stranger = withToken(register(uniqueUsername()));
        publish(stranger, createDraft(stranger, draft()));
        String authorId = idOf(author);
        RestTestClient reader = withToken(register(uniqueUsername()));

        expectFeed(reader, List.of());
        client.get().uri(API + "/feed").exchange().expectStatus().isUnauthorized();

        reader.put().uri(API + "/users/{id}/follow", authorId).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/like", published).exchange().expectStatus().isOk();
        eventually(() -> reader.get().uri(API + "/feed")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(published))
                .jsonPath("$.data.list[0].title").isEqualTo("Redis 计数实践")
                .jsonPath("$.data.list[0].summary").isEqualTo("手写摘要")
                .jsonPath("$.data.list[0].publishedAt").isNotEmpty()
                .jsonPath("$.data.list[0].author.id").isEqualTo(authorId)
                .jsonPath("$.data.list[0].author.nickname").isEqualTo(authorName)
                .jsonPath("$.data.list[0].counts.likeCount").isEqualTo(1)
                .jsonPath("$.data.list[0].content").doesNotExist()
                .jsonPath("$.data.hasMore").isEqualTo(false));

        reader.delete().uri(API + "/users/{id}/follow", authorId).exchange().expectStatus().isOk();
        expectFeed(reader, List.of());
    }

    @Test
    void feedIsPagedByCursorWithoutDuplicatesOrGaps() {
        RestTestClient alice = withToken(register(uniqueUsername()));
        RestTestClient bob = withToken(register(uniqueUsername()));
        List<String> newestFirst = new ArrayList<>();
        for (RestTestClient author : List.of(alice, bob, bob, alice, bob)) {
            newestFirst.addFirst(publish(author, createDraft(author, draft())));
        }
        RestTestClient reader = withToken(register(uniqueUsername()));
        for (RestTestClient author : List.of(alice, bob)) {
            reader.put().uri(API + "/users/{id}/follow", idOf(author)).exchange().expectStatus().isOk();
        }

        eventually(() -> assertThat(readAllPages(reader, 2)).isEqualTo(newestFirst));
    }

    /**
     * 按 size 连续翻页直到没有下一页，返回依次看到的文章 ID。
     */
    protected List<String> readAllPages(RestTestClient reader, int size) {
        List<String> seen = new ArrayList<>();
        AtomicReference<String> cursor = new AtomicReference<>();
        AtomicReference<Boolean> hasMore = new AtomicReference<>(true);
        while (hasMore.get()) {
            RestTestClient.BodyContentSpec page = reader.get()
                    .uri(API + "/feed?size=" + size + (cursor.get() == null ? "" : "&cursor=" + cursor.get()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.list[*].id").value(List.class, seen::addAll)
                    .jsonPath("$.data.hasMore").value(Boolean.class, hasMore::set);
            if (hasMore.get()) {
                page.jsonPath("$.data.nextCursor").value(String.class, cursor::set);
            }
        }
        return seen;
    }

    protected void expectFeed(RestTestClient reader, List<String> articleIds) {
        reader.get().uri(API + "/feed")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(articleIds)
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    protected String idOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }
}
