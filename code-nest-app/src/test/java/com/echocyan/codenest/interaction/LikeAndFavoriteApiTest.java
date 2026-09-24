package com.echocyan.codenest.interaction;

import com.echocyan.codenest.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

class LikeAndFavoriteApiTest extends IntegrationTest {

    @Test
    void likeIsIdempotentAndCountsForArticleAndAuthor() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));

        like(reader, articleId).expectStatus().isOk();
        like(reader, articleId).expectStatus().isOk();
        expectLikes(articleId, 1);

        unlike(reader, articleId).expectStatus().isOk();
        unlike(reader, articleId).expectStatus().isOk();
        expectLikes(articleId, 0);
    }

    @Test
    void concurrentLikesOnOneArticleAreCountedExactly() throws Exception {
        String articleId = publishedArticle(withToken(register(uniqueUsername())));
        List<RestTestClient> readers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            readers.add(withToken(register(uniqueUsername())));
        }
        // 每位读者都连点三次，模拟重复提交
        List<RestTestClient> requests = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            requests.addAll(readers);
        }

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            List<Future<?>> futures = requests.stream()
                    .<Future<?>>map(reader -> executor.submit(() -> {
                        start.await();
                        like(reader, articleId).expectStatus().isOk();
                        return null;
                    }))
                    .toList();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        expectLikes(articleId, 20);
    }

    @Test
    void favoriteIsIdempotentAndCountsForArticle() {
        String articleId = publishedArticle(withToken(register(uniqueUsername())));
        RestTestClient reader = withToken(register(uniqueUsername()));

        favorite(reader, articleId).expectStatus().isOk();
        favorite(reader, articleId).expectStatus().isOk();
        expectFavorites(articleId, 1);

        unfavorite(reader, articleId).expectStatus().isOk();
        unfavorite(reader, articleId).expectStatus().isOk();
        expectFavorites(articleId, 0);
    }

    @Test
    void myFavoritesArePagedNewestFirst() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = publishedArticle(author);
        String second = publishedArticle(author);
        String third = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        for (String articleId : List.of(second, first, third)) {
            favorite(reader, articleId).expectStatus().isOk();
        }

        AtomicReference<String> cursor = new AtomicReference<>();
        reader.get().uri(API + "/users/me/favorites?size=2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(third, first))
                .jsonPath("$.data.list[0].article.title").isEqualTo("点赞计数")
                .jsonPath("$.data.list[0].favoritedAt").isNotEmpty()
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);

        reader.get().uri(API + "/users/me/favorites?size=2&cursor={cursor}", cursor.get())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(second))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void deletedArticlesDropOutOfMyFavorites() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String kept = publishedArticle(author);
        String deleted = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        favorite(reader, kept).expectStatus().isOk();
        favorite(reader, deleted).expectStatus().isOk();

        author.delete().uri(API + "/articles/{id}", deleted).exchange().expectStatus().isOk();

        reader.get().uri(API + "/users/me/favorites")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].article.id").isEqualTo(List.of(kept))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void statesTellWhichArticlesILikedOrFavorited() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String liked = publishedArticle(author);
        String favorited = publishedArticle(author);
        String untouched = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        like(reader, liked).expectStatus().isOk();
        favorite(reader, favorited).expectStatus().isOk();
        // 别人的点赞不影响我的状态
        like(author, favorited).expectStatus().isOk();

        reader.get().uri(API + "/articles/states?ids={ids}", String.join(",", liked, favorited, untouched))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data['%s'].liked".formatted(liked)).isEqualTo(true)
                .jsonPath("$.data['%s'].favorited".formatted(liked)).isEqualTo(false)
                .jsonPath("$.data['%s'].liked".formatted(favorited)).isEqualTo(false)
                .jsonPath("$.data['%s'].favorited".formatted(favorited)).isEqualTo(true)
                .jsonPath("$.data['%s'].liked".formatted(untouched)).isEqualTo(false)
                .jsonPath("$.data['%s'].favorited".formatted(untouched)).isEqualTo(false);
    }

    @Test
    void statesAcceptAtMostFiftyIds() {
        RestTestClient reader = withToken(register(uniqueUsername()));
        String fifty = String.join(",", IntStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList());
        String fiftyOne = fifty + ",51";

        reader.get().uri(API + "/articles/states?ids={ids}", fifty)
                .exchange()
                .expectStatus().isOk();
        reader.get().uri(API + "/articles/states?ids={ids}", fiftyOne)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(90400);
    }

    @Test
    void onlyPublishedArticlesCanBeLikedOrFavorited() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String draftId = draft(author);
        String deletedId = publishedArticle(author);
        author.delete().uri(API + "/articles/{id}", deletedId).exchange().expectStatus().isOk();
        RestTestClient reader = withToken(register(uniqueUsername()));

        for (String articleId : List.of(draftId, deletedId, "1")) {
            for (String action : List.of("like", "favorite")) {
                for (RestTestClient.ResponseSpec response : List.of(
                        reader.put().uri(API + "/articles/{id}/{action}", articleId, action).exchange(),
                        reader.delete().uri(API + "/articles/{id}/{action}", articleId, action).exchange())) {
                    response.expectStatus().isNotFound()
                            .expectBody().jsonPath("$.code").isEqualTo(30001);
                }
            }
        }
    }

    private RestTestClient.ResponseSpec like(RestTestClient reader, String articleId) {
        return reader.put().uri(API + "/articles/{id}/like", articleId).exchange();
    }

    private RestTestClient.ResponseSpec unlike(RestTestClient reader, String articleId) {
        return reader.delete().uri(API + "/articles/{id}/like", articleId).exchange();
    }

    private RestTestClient.ResponseSpec favorite(RestTestClient reader, String articleId) {
        return reader.put().uri(API + "/articles/{id}/favorite", articleId).exchange();
    }

    private RestTestClient.ResponseSpec unfavorite(RestTestClient reader, String articleId) {
        return reader.delete().uri(API + "/articles/{id}/favorite", articleId).exchange();
    }

    private void expectFavorites(String articleId, int expected) {
        eventually(() -> client.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectBody().jsonPath("$.data.counts.favoriteCount").isEqualTo(expected));
    }

    /**
     * 断言文章点赞数与作者获赞数（测试里每位作者只有一篇文章，两者相等）。
     */
    private void expectLikes(String articleId, int expected) {
        AtomicReference<String> authorId = new AtomicReference<>();
        eventually(() -> client.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.counts.likeCount").isEqualTo(expected)
                .jsonPath("$.data.author.id").value(String.class, authorId::set));
        eventually(() -> client.get().uri(API + "/users/{id}", authorId.get())
                .exchange()
                .expectBody().jsonPath("$.data.counts.likeReceivedCount").isEqualTo(expected));
    }

    private String publishedArticle(RestTestClient author) {
        String id = draft(author);
        author.post().uri(API + "/articles/{id}/publish", id).exchange().expectStatus().isOk();
        return id;
    }

    private String draft(RestTestClient author) {
        AtomicReference<String> id = new AtomicReference<>();
        author.post().uri(API + "/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "点赞计数", "content", "热点行更新会排队等锁。", "categoryId", 1,
                        "tagIds", List.of(1)))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }
}
