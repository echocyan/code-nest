package com.echocyan.codenest.counter;

import com.echocyan.codenest.article.ArticleTestSupport;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 计数对账：篡改计数后经管理端口触发对账，各项计数恢复为关系表、内容表中的真实值。
 */
class CounterReconcileApiTest extends ArticleTestSupport {

    @Autowired
    private CounterApi counterApi;

    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void reconcileRestoresTamperedCounts() {
        RestTestClient author = withToken(register(uniqueUsername()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient replier = withToken(register(uniqueUsername()));
        String article = publish(author, createDraft(author, draft()));
        String untouched = publish(author, createDraft(author, draft()));
        String authorId = authorIdOf(author, article);
        String readerId = idOf(reader);
        String replierId = idOf(replier);
        reader.put().uri(API + "/articles/{id}/like", article).exchange().expectStatus().isOk();
        replier.put().uri(API + "/articles/{id}/like", article).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/favorite", article).exchange().expectStatus().isOk();
        reader.put().uri(API + "/users/{id}/follow", authorId).exchange().expectStatus().isOk();
        String comment = comment(reader, article);
        replier.post().uri(API + "/comments/{id}/replies", comment)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "同意"))
                .exchange()
                .expectStatus().isOk();
        // 等异步计数全部生效后再篡改，避免在途的增量落在篡改之后
        eventually(() -> expectArticleCounts(article, 2, 1, 2));
        eventually(() -> expectUserCounts(authorId, 1, 0, 2, 2));
        eventually(() -> expectUserCounts(readerId, 0, 1, 0, 0));
        eventually(() -> expectReplyCount(article, 1));

        counterApi.reset(CounterMetric.ARTICLE_LIKE, id(article), 100);
        counterApi.reset(CounterMetric.ARTICLE_FAVORITE, id(article), 0);
        counterApi.reset(CounterMetric.ARTICLE_COMMENT, id(article), 50);
        counterApi.reset(CounterMetric.COMMENT_REPLY, id(comment), 9);
        counterApi.reset(CounterMetric.USER_FOLLOWER, id(authorId), 30);
        counterApi.reset(CounterMetric.USER_FOLLOWING, id(readerId), 0);
        counterApi.reset(CounterMetric.USER_ARTICLE, id(authorId), 7);
        counterApi.reset(CounterMetric.USER_LIKE_RECEIVED, id(authorId), 0);
        // 计数表里有值、关系表里没有对应行
        counterApi.reset(CounterMetric.ARTICLE_LIKE, id(untouched), 5);
        counterApi.reset(CounterMetric.ARTICLE_FAVORITE, id(untouched), 5);
        counterApi.reset(CounterMetric.ARTICLE_COMMENT, id(untouched), 5);
        counterApi.reset(CounterMetric.USER_FOLLOWER, id(replierId), 5);
        counterApi.reset(CounterMetric.USER_FOLLOWING, id(replierId), 5);
        counterApi.reset(CounterMetric.USER_ARTICLE, id(replierId), 5);
        counterApi.reset(CounterMetric.USER_LIKE_RECEIVED, id(replierId), 5);

        RestTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build()
                .post().uri("/actuator/counter-reconcile")
                .exchange()
                .expectStatus().isOk();

        expectArticleCounts(article, 2, 1, 2);
        expectArticleCounts(untouched, 0, 0, 0);
        expectReplyCount(article, 1);
        expectUserCounts(authorId, 1, 0, 2, 2);
        expectUserCounts(readerId, 0, 1, 0, 0);
        expectUserCounts(replierId, 0, 0, 0, 0);
    }

    private String comment(RestTestClient user, String articleId) {
        AtomicReference<String> id = new AtomicReference<>();
        user.post().uri(API + "/articles/{id}/comments", articleId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "写得好"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    private void expectArticleCounts(String articleId, int likes, int favorites, int comments) {
        client.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.counts.likeCount").isEqualTo(likes)
                .jsonPath("$.data.counts.favoriteCount").isEqualTo(favorites)
                .jsonPath("$.data.counts.commentCount").isEqualTo(comments);
    }

    private void expectReplyCount(String articleId, int replies) {
        client.get().uri(API + "/articles/{id}/comments", articleId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.list[0].replyCount").isEqualTo(replies);
    }

    private void expectUserCounts(String userId, int followers, int followings, int articles, int likesReceived) {
        client.get().uri(API + "/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.counts.followerCount").isEqualTo(followers)
                .jsonPath("$.data.counts.followingCount").isEqualTo(followings)
                .jsonPath("$.data.counts.articleCount").isEqualTo(articles)
                .jsonPath("$.data.counts.likeReceivedCount").isEqualTo(likesReceived);
    }

    private String idOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    private static long id(String id) {
        return Long.parseLong(id);
    }
}
