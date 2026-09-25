package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.article.ArticleTestSupport;
import com.echocyan.codenest.support.RateLimitEnabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限额见 {@link RateLimitEnabled}：每 IP 每小时注册 3 次、每分钟登录 3 次、搜索 2 次；每用户每小时发布 1 篇，
 * 每分钟点赞与收藏 2 次、关注与取关 2 次，评论和回复每分钟 5 条、每天 3 条。
 * 每个测试经可信代理（本机）以一个新的客户端 IP 发请求，测试之间互不占用额度；一个测试最多注册 3 个用户。
 */
@RateLimitEnabled
class RateLimitApiTest extends ArticleTestSupport {

    private static final String XFF = "X-Forwarded-For";

    private static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    private static RestTestClient.ResponseSpec postLogin(RestTestClient client, String username) {
        return client.post().uri(API + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", PASSWORD))
                .exchange();
    }

    private static RestTestClient.ResponseSpec follow(RestTestClient user, String userId) {
        return user.put().uri(API + "/users/{id}/follow", userId).exchange();
    }

    private static RestTestClient.ResponseSpec comment(RestTestClient user, String articleId) {
        return user.post().uri(API + "/articles/{id}/comments", articleId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "写得好"))
                .exchange();
    }

    private static RestTestClient.ResponseSpec reply(RestTestClient user, String commentId) {
        return user.post().uri(API + "/comments/{id}/replies", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "同意"))
                .exchange();
    }

    private static String userIdOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    @BeforeEach
    void useFreshClientIp() {
        client = behind(randomIp());
    }

    @Test
    void exceedingLimitReturns429WithRetryAfter() {
        String username = uniqueUsername();
        register(username);
        for (int i = 0; i < 3; i++) {
            login(username);
        }

        postLogin(client, username)
                .expectStatus().isEqualTo(429)
                .expectHeader().value(HttpHeaders.RETRY_AFTER,
                        value -> assertThat(Long.parseLong(value)).isBetween(1L, 60L))
                .expectBody().jsonPath("$.code").isEqualTo(90429);
    }

    @Test
    void differentIpsHaveSeparateQuotas() {
        String username = uniqueUsername();
        register(username);
        for (int i = 0; i < 3; i++) {
            login(username);
        }
        postLogin(client, username).expectStatus().isEqualTo(429);

        postLogin(behind(randomIp()), username).expectStatus().isOk();
    }

    @Test
    void differentUsersHaveSeparateQuotas() {
        String authorId = userIdOf(withToken(register(uniqueUsername())));
        RestTestClient first = withToken(register(uniqueUsername()));
        RestTestClient second = withToken(register(uniqueUsername()));
        follow(first, authorId).expectStatus().isOk();
        first.delete().uri(API + "/users/{id}/follow", authorId).exchange().expectStatus().isOk();
        follow(first, authorId).expectStatus().isEqualTo(429);

        follow(second, authorId).expectStatus().isOk();
    }

    @Test
    void commentsAndRepliesShareTheDailyQuota() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String article = publish(author, createDraft(author, draft()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        AtomicReference<String> commentId = new AtomicReference<>();
        comment(reader, article).expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, commentId::set);
        comment(reader, article).expectStatus().isOk();
        reply(reader, commentId.get()).expectStatus().isOk();

        reply(reader, commentId.get())
                .expectStatus().isEqualTo(429)
                // 每分钟的额度还有余量，等待时间由每天的额度决定
                .expectHeader().value(HttpHeaders.RETRY_AFTER,
                        value -> assertThat(Long.parseLong(value)).isGreaterThan(60L));
    }

    @Test
    void registrationIsLimitedPerIp() {
        for (int i = 0; i < 3; i++) {
            register(uniqueUsername());
        }

        client.post().uri(API + "/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", uniqueUsername(), "password", PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void anonymousSearchIsLimitedPerIp() {
        search().expectStatus().isOk();
        search().expectStatus().isOk();

        search().expectStatus().isEqualTo(429);
    }

    @Test
    void publishingIsLimitedPerUser() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String first = createDraft(author, draft());
        String second = createDraft(author, draft());
        publish(author, first);

        author.post().uri(API + "/articles/{id}/publish", second)
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    @Test
    void likesFavoritesAndCancellationsShareOneQuota() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String article = publish(author, createDraft(author, draft()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        reader.put().uri(API + "/articles/{id}/like", article).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/favorite", article).exchange().expectStatus().isOk();

        reader.delete().uri(API + "/articles/{id}/favorite", article)
                .exchange()
                .expectStatus().isEqualTo(429);
    }

    private RestTestClient behind(String clientIp) {
        return client.mutate().defaultHeader(XFF, clientIp).build();
    }

    private RestTestClient.ResponseSpec search() {
        return client.get().uri(API + "/search/articles?q=redis").exchange();
    }
}
