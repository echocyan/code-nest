package com.echocyan.codenest.notification;

import static org.awaitility.Awaitility.await;

import com.echocyan.codenest.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 通知由 MQ 消费者异步写入，断言前先用 Awaitility 等到通知出现。
 */
class NotificationApiTest extends IntegrationTest {

    private static final String TITLE = "通知里的文章";

    @Test
    void likeNotifiesTheAuthor() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        String readerName = uniqueUsername();
        RestTestClient reader = withToken(register(readerName));

        like(reader, articleId);

        awaitNotifications(author, 1);
        author.get().uri(API + "/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[0].type").isEqualTo("LIKE")
                .jsonPath("$.data.list[0].actor.id").isEqualTo(idOf(reader))
                .jsonPath("$.data.list[0].actor.nickname").isEqualTo(readerName)
                .jsonPath("$.data.list[0].articleId").isEqualTo(articleId)
                .jsonPath("$.data.list[0].articleTitle").isEqualTo(TITLE)
                .jsonPath("$.data.list[0].commentId").isEmpty()
                .jsonPath("$.data.list[0].read").isEqualTo(false)
                .jsonPath("$.data.list[0].createdAt").isNotEmpty();
    }

    @Test
    void repeatedLikesNotifyOnceAndSelfLikesNotAtAll() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        like(author, articleId);
        like(reader, articleId);
        reader.delete().uri(API + "/articles/{id}/like", articleId).exchange().expectStatus().isOk();
        like(reader, articleId);
        // 消费者按顺序处理，等到最后这条出现，前面的消息都已处理完
        RestTestClient last = withToken(register(uniqueUsername()));
        like(last, articleId);

        awaitNotifications(author, 2);
        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].actor.id").isEqualTo(List.of(idOf(last), idOf(reader)));
    }

    @Test
    void commentNotifiesTheAuthorAndReplyNotifiesTheRepliedUser() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        comment(author, articleId, "作者自己的评论");
        RestTestClient commenter = withToken(register(uniqueUsername()));
        String commentId = comment(commenter, articleId, "好".repeat(150));
        reply(commenter, commentId, Map.of("content", "自己回复自己"));
        RestTestClient replier = withToken(register(uniqueUsername()));
        String replyId = reply(replier, commentId, Map.of("content", "同意"));
        String mentionId = reply(commenter, commentId, Map.of("content", "@回复者", "replyToUserId", idOf(replier)));

        awaitNotifications(author, 1);
        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].type").isEqualTo("COMMENT")
                .jsonPath("$.data.list[0].actor.id").isEqualTo(idOf(commenter))
                .jsonPath("$.data.list[0].articleTitle").isEqualTo(TITLE)
                .jsonPath("$.data.list[0].commentId").isEqualTo(commentId)
                .jsonPath("$.data.list[0].commentSummary").isEqualTo("好".repeat(100));
        awaitNotifications(commenter, 1);
        commenter.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].type").isEqualTo("REPLY")
                .jsonPath("$.data.list[0].actor.id").isEqualTo(idOf(replier))
                .jsonPath("$.data.list[0].articleId").isEqualTo(articleId)
                .jsonPath("$.data.list[0].commentId").isEqualTo(replyId)
                .jsonPath("$.data.list[0].commentSummary").isEqualTo("同意");
        awaitNotifications(replier, 1);
        replier.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].type").isEqualTo("REPLY")
                .jsonPath("$.data.list[0].commentId").isEqualTo(mentionId);
    }

    @Test
    void repeatedFollowsNotifyOnce() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String authorId = idOf(author);
        RestTestClient fan = withToken(register(uniqueUsername()));
        follow(fan, authorId);
        fan.delete().uri(API + "/users/{id}/follow", authorId).exchange().expectStatus().isOk();
        follow(fan, authorId);
        RestTestClient last = withToken(register(uniqueUsername()));
        follow(last, authorId);

        awaitNotifications(author, 2);
        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].type").isEqualTo(List.of("FOLLOW", "FOLLOW"))
                .jsonPath("$.data.list[*].actor.id").isEqualTo(List.of(idOf(last), idOf(fan)))
                .jsonPath("$.data.list[0].articleId").isEmpty()
                .jsonPath("$.data.list[0].articleTitle").isEmpty();
    }

    @Test
    void unreadCountStopsAtOneHundred() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        for (int i = 0; i < 101; i++) {
            comment(reader, articleId, "第 " + i + " 条");
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> author.get().uri(API + "/notifications?size=1")
                .exchange()
                .expectBody().jsonPath("$.data.list[0].commentSummary").isEqualTo("第 100 条"));
        expectUnread(author, 100);
    }

    @Test
    void notificationsAreMarkedReadOnlyExplicitly() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        like(reader, articleId);
        like(withToken(register(uniqueUsername())), articleId);
        awaitNotifications(author, 2);
        expectUnread(author, 2);

        AtomicReference<String> older = new AtomicReference<>();
        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody().jsonPath("$.data.list[1].id").value(String.class, older::set);
        reader.put().uri(API + "/notifications/{id}/read", older.get())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(50001);
        for (int i = 0; i < 2; i++) {
            author.put().uri(API + "/notifications/{id}/read", older.get()).exchange().expectStatus().isOk();
        }
        expectUnread(author, 1);
        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody().jsonPath("$.data.list[*].read").isEqualTo(List.of(false, true));

        author.put().uri(API + "/notifications/read-all").exchange().expectStatus().isOk();
        expectUnread(author, 0);
    }

    @Test
    void deletedContentIsShownAsDeleted() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String kept = publishedArticle(author);
        String deleted = publishedArticle(author);
        RestTestClient reader = withToken(register(uniqueUsername()));
        String commentId = comment(reader, kept, "要删掉的评论");
        like(reader, deleted);
        awaitNotifications(author, 2);

        reader.delete().uri(API + "/comments/{id}", commentId).exchange().expectStatus().isOk();
        author.delete().uri(API + "/articles/{id}", deleted).exchange().expectStatus().isOk();

        author.get().uri(API + "/notifications")
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].type").isEqualTo(List.of("LIKE", "COMMENT"))
                .jsonPath("$.data.list[0].articleTitle").isEqualTo("该内容已删除")
                .jsonPath("$.data.list[1].articleTitle").isEqualTo(TITLE)
                .jsonPath("$.data.list[1].commentSummary").isEqualTo("该内容已删除");
    }

    /**
     * 等到通知列表里恰好有 expected 条。
     */
    private void awaitNotifications(RestTestClient recipient, int expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> recipient.get().uri(API + "/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.list.length()").isEqualTo(expected));
    }

    private void expectUnread(RestTestClient recipient, int expected) {
        recipient.get().uri(API + "/notifications/unread-count")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data").isEqualTo(expected);
    }

    private String comment(RestTestClient user, String articleId, String content) {
        AtomicReference<String> id = new AtomicReference<>();
        user.post().uri(API + "/articles/{id}/comments", articleId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    private String reply(RestTestClient user, String commentId, Map<String, String> body) {
        AtomicReference<String> id = new AtomicReference<>();
        user.post().uri(API + "/comments/{id}/replies", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    private void follow(RestTestClient follower, String userId) {
        follower.put().uri(API + "/users/{id}/follow", userId).exchange().expectStatus().isOk();
    }

    private void like(RestTestClient reader, String articleId) {
        reader.put().uri(API + "/articles/{id}/like", articleId).exchange().expectStatus().isOk();
    }

    private String publishedArticle(RestTestClient author) {
        AtomicReference<String> id = new AtomicReference<>();
        author.post().uri(API + "/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", TITLE, "content", "正文", "categoryId", 1, "tagIds", List.of(1)))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        author.post().uri(API + "/articles/{id}/publish", id.get()).exchange().expectStatus().isOk();
        return id.get();
    }

    private String idOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }
}
