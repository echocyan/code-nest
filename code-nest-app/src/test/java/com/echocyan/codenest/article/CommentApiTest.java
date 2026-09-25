package com.echocyan.codenest.article;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommentApiTest extends IntegrationTest {

    @Autowired
    private ArticleApi articleApi;

    private static String idOf(RestTestClient.ResponseSpec response) {
        AtomicReference<String> id = new AtomicReference<>();
        response.expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    @Test
    void readerCanCommentOnAPublishedArticle() {
        String article = publishedArticle();
        String username = uniqueUsername();
        RestTestClient reader = withToken(register(username));

        String id = comment(reader, article, "写得好");

        client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list.length()").isEqualTo(1)
                .jsonPath("$.data.list[0].id").isEqualTo(id)
                .jsonPath("$.data.list[0].content").isEqualTo("写得好")
                .jsonPath("$.data.list[0].deleted").isEqualTo(false)
                .jsonPath("$.data.list[0].author.nickname").isEqualTo(username)
                .jsonPath("$.data.list[0].replyCount").isEqualTo(0)
                .jsonPath("$.data.list[0].createdAt").isNotEmpty()
                .jsonPath("$.data.hasMore").isEqualTo(false)
                .jsonPath("$.data.nextCursor").isEmpty();
    }

    @Test
    void commentingOnAnArticleThatIsNotPublishedIsNotFound() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String draft = draft(author);
        String deleted = publish(author, draft(author));
        author.delete().uri(API + "/articles/{id}", deleted).exchange().expectStatus().isOk();

        for (String article : List.of(draft, deleted, "1")) {
            postComment(author, article, "写得好")
                    .expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo(20001);
            client.get().uri(API + "/articles/{id}/comments", article)
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Test
    void commentContentIsLimitedTo1000Characters() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));

        comment(reader, article, "评".repeat(1000));
        for (String content : List.of("评".repeat(1001), " ")) {
            postComment(reader, article, content)
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
    }

    @Test
    void commentingRequiresLogin() {
        postComment(client, publishedArticle(), "写得好").expectStatus().isUnauthorized();
    }

    @Test
    void everyCommentCountsTowardsTheArticle() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));

        comment(reader, article, "第一条");
        comment(reader, article, "第二条");

        expectCommentCount(article, 2);
    }

    @Test
    void commentsArePagedNewestFirst() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));
        String first = comment(reader, article, "1");
        String second = comment(reader, article, "2");
        String third = comment(reader, article, "3");

        AtomicReference<String> cursor = new AtomicReference<>();
        client.get().uri(API + "/articles/{id}/comments?size=2", article)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(third, second))
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);

        client.get().uri(API + "/articles/{id}/comments?size=2&cursor={cursor}", article, cursor.get())
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(first))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void pageSizeIsBetween1And50() {
        String article = publishedArticle();

        for (int size : List.of(0, 51)) {
            client.get().uri(API + "/articles/{id}/comments?size={size}", article, size)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
    }

    @Test
    void readerCanReplyToAComment() {
        String article = publishedArticle();
        String commenterName = uniqueUsername();
        String replierName = uniqueUsername();
        String commentId = comment(withToken(register(commenterName)), article, "写得好");
        RestTestClient replier = withToken(register(replierName));

        String replyId = reply(replier, commentId, Map.of("content", "同意"));

        client.get().uri(API + "/comments/{id}/replies", commentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list.length()").isEqualTo(1)
                .jsonPath("$.data.list[0].id").isEqualTo(replyId)
                .jsonPath("$.data.list[0].rootId").isEqualTo(commentId)
                .jsonPath("$.data.list[0].content").isEqualTo("同意")
                .jsonPath("$.data.list[0].author.nickname").isEqualTo(replierName)
                .jsonPath("$.data.list[0].replyTo").isEmpty()
                .jsonPath("$.data.list[0].createdAt").isNotEmpty()
                .jsonPath("$.data.hasMore").isEqualTo(false);
        eventually(() -> client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(commentId))
                .jsonPath("$.data.list[0].replyCount").isEqualTo(1));
        expectCommentCount(article, 2);
    }

    @Test
    void replyingToAReplyStaysUnderTheSameComment() {
        String article = publishedArticle();
        String commentId = comment(withToken(register(uniqueUsername())), article, "写得好");
        String firstReplierName = uniqueUsername();
        String firstReply = reply(withToken(register(firstReplierName)), commentId, Map.of("content", "同意"));

        String secondReply = reply(withToken(register(uniqueUsername())), firstReply, Map.of("content", "+1"));

        client.get().uri(API + "/comments/{id}/replies", commentId)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(firstReply, secondReply))
                .jsonPath("$.data.list[1].rootId").isEqualTo(commentId)
                .jsonPath("$.data.list[1].replyTo.nickname").isEqualTo(firstReplierName);
        eventually(() -> client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[0].replyCount").isEqualTo(2));
        client.get().uri(API + "/comments/{id}/replies", firstReply)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(20005);
    }

    @Test
    void replyCanMentionAnyoneInTheDiscussion() {
        String article = publishedArticle();
        String commenterName = uniqueUsername();
        RestTestClient commenter = withToken(register(commenterName));
        String commentId = comment(commenter, article, "写得好");
        String replierName = uniqueUsername();
        RestTestClient replier = withToken(register(replierName));
        reply(replier, commentId, Map.of("content", "同意"));
        String commenterId = userIdOf(commenter);
        String replierId = userIdOf(replier);

        reply(replier, commentId, Map.of("content", "@评论者", "replyToUserId", commenterId));
        reply(commenter, commentId, Map.of("content", "@回复者", "replyToUserId", replierId));

        client.get().uri(API + "/comments/{id}/replies", commentId)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[1:].replyTo.nickname").isEqualTo(List.of(commenterName, replierName));
    }

    @Test
    void replyCannotMentionSomeoneOutsideTheDiscussion() {
        String article = publishedArticle();
        String commentId = comment(withToken(register(uniqueUsername())), article, "写得好");
        RestTestClient outsider = withToken(register(uniqueUsername()));
        comment(outsider, article, "另一条");
        String outsiderId = userIdOf(outsider);

        postReply(withToken(register(uniqueUsername())), commentId,
                Map.of("content", "@路人", "replyToUserId", outsiderId))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(20006);
    }

    @Test
    void replyingToAnUnknownCommentIsNotFound() {
        postReply(withToken(register(uniqueUsername())), "1", Map.of("content", "同意"))
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(20005);
        client.get().uri(API + "/comments/{id}/replies", "1")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void repliesArePagedOldestFirst() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));
        String commentId = comment(reader, article, "写得好");
        String first = reply(reader, commentId, Map.of("content", "1"));
        String second = reply(reader, commentId, Map.of("content", "2"));
        String third = reply(reader, commentId, Map.of("content", "3"));

        AtomicReference<String> cursor = new AtomicReference<>();
        client.get().uri(API + "/comments/{id}/replies?size=2", commentId)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(first, second))
                .jsonPath("$.data.hasMore").isEqualTo(true)
                .jsonPath("$.data.nextCursor").value(String.class, cursor::set);

        client.get().uri(API + "/comments/{id}/replies?size=2&cursor={cursor}", commentId, cursor.get())
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(third))
                .jsonPath("$.data.hasMore").isEqualTo(false);
    }

    @Test
    void deletedCommentWithoutRepliesDisappears() {
        String article = publishedArticle();
        RestTestClient commenter = withToken(register(uniqueUsername()));
        String kept = comment(commenter, article, "保留");
        String deleted = comment(commenter, article, "删除");

        delete(commenter, deleted).expectStatus().isOk();

        client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody().jsonPath("$.data.list[*].id").isEqualTo(List.of(kept));
        expectCommentCount(article, 1);
    }

    @Test
    void deletedCommentWithRepliesKeepsItsPlace() {
        String article = publishedArticle();
        RestTestClient commenter = withToken(register(uniqueUsername()));
        String commentId = comment(commenter, article, "写得好");
        String replyId = reply(withToken(register(uniqueUsername())), commentId, Map.of("content", "同意"));

        delete(commenter, commentId).expectStatus().isOk();

        eventually(() -> client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody()
                .jsonPath("$.data.list[*].id").isEqualTo(List.of(commentId))
                .jsonPath("$.data.list[0].content").isEqualTo("该评论已删除")
                .jsonPath("$.data.list[0].deleted").isEqualTo(true)
                .jsonPath("$.data.list[0].author").isEmpty()
                .jsonPath("$.data.list[0].replyCount").isEqualTo(1));
        client.get().uri(API + "/comments/{id}/replies", commentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.list[*].id").isEqualTo(List.of(replyId));
        expectCommentCount(article, 1);
    }

    @Test
    void deletingTheLastReplyOfADeletedCommentRemovesIt() {
        String article = publishedArticle();
        RestTestClient commenter = withToken(register(uniqueUsername()));
        String commentId = comment(commenter, article, "写得好");
        RestTestClient replier = withToken(register(uniqueUsername()));
        String replyId = reply(replier, commentId, Map.of("content", "同意"));
        delete(commenter, commentId).expectStatus().isOk();

        delete(replier, replyId).expectStatus().isOk();

        client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody().jsonPath("$.data.list").isEmpty();
        expectCommentCount(article, 0);
    }

    @Test
    void deletedReplyDisappearsAndStopsCounting() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));
        String commentId = comment(reader, article, "写得好");
        String kept = reply(reader, commentId, Map.of("content", "保留"));
        String deleted = reply(reader, commentId, Map.of("content", "删除"));

        delete(reader, deleted).expectStatus().isOk();

        client.get().uri(API + "/comments/{id}/replies", commentId)
                .exchange()
                .expectBody().jsonPath("$.data.list[*].id").isEqualTo(List.of(kept));
        eventually(() -> client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody().jsonPath("$.data.list[0].replyCount").isEqualTo(1));
        expectCommentCount(article, 2);
    }

    @Test
    void deletedCommentCannotBeRepliedToOrDeletedAgain() {
        String article = publishedArticle();
        RestTestClient commenter = withToken(register(uniqueUsername()));
        String commentId = comment(commenter, article, "写得好");
        delete(commenter, commentId).expectStatus().isOk();

        postReply(commenter, commentId, Map.of("content", "同意"))
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(20005);
        delete(commenter, commentId)
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(20005);
        expectCommentCount(article, 0);
    }

    @Test
    void onlyTheCommenterCanDelete() {
        String article = publishedArticle();
        String commentId = comment(withToken(register(uniqueUsername())), article, "写得好");

        delete(withToken(register(uniqueUsername())), commentId)
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(90403);
        client.get().uri(API + "/articles/{id}/comments", article)
                .exchange()
                .expectBody().jsonPath("$.data.list[*].id").isEqualTo(List.of(commentId));
    }

    @Test
    void commentBriefsCoverLiveCommentsAndReplies() {
        String article = publishedArticle();
        RestTestClient reader = withToken(register(uniqueUsername()));
        String commentId = comment(reader, article, "写得好");
        String replyId = reply(reader, commentId, Map.of("content", "同意"));
        String deletedId = reply(reader, commentId, Map.of("content", "删除"));
        delete(reader, deletedId).expectStatus().isOk();
        long readerId = Long.parseLong(userIdOf(reader));

        Map<Long, CommentBrief> briefs = articleApi.getCommentBriefs(Stream.of(commentId, replyId, deletedId, "1")
                .map(Long::valueOf)
                .toList());

        assertThat(briefs).containsOnlyKeys(Long.valueOf(commentId), Long.valueOf(replyId));
        assertThat(briefs.get(Long.valueOf(replyId))).isEqualTo(new CommentBrief(
                Long.valueOf(replyId), Long.valueOf(article), readerId, Long.valueOf(commentId), "同意"));
        assertThat(briefs.get(Long.valueOf(commentId)).rootId()).isZero();
    }

    private RestTestClient.ResponseSpec delete(RestTestClient user, String commentId) {
        return user.delete().uri(API + "/comments/{id}", commentId).exchange();
    }

    private String userIdOf(RestTestClient user) {
        AtomicReference<String> id = new AtomicReference<>();
        user.get().uri(API + "/users/me")
                .exchange()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    private String reply(RestTestClient user, String commentId, Map<String, Object> body) {
        return idOf(postReply(user, commentId, body).expectStatus().isOk());
    }

    private RestTestClient.ResponseSpec postReply(RestTestClient user, String commentId, Map<String, Object> body) {
        return user.post().uri(API + "/comments/{id}/replies", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private void expectCommentCount(String articleId, int expected) {
        eventually(() -> client.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.counts.commentCount").isEqualTo(expected));
    }

    /**
     * 新注册一个作者，发布一篇文章，返回文章 ID。
     */
    private String publishedArticle() {
        RestTestClient author = withToken(register(uniqueUsername()));
        return publish(author, draft(author));
    }

    private String draft(RestTestClient author) {
        return idOf(author.post().uri(API + "/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "评论测试", "content", "正文", "categoryId", 1))
                .exchange()
                .expectStatus().isOk());
    }

    private String publish(RestTestClient author, String articleId) {
        author.post().uri(API + "/articles/{id}/publish", articleId).exchange().expectStatus().isOk();
        return articleId;
    }

    private String comment(RestTestClient user, String articleId, String content) {
        return idOf(postComment(user, articleId, content).expectStatus().isOk());
    }

    private RestTestClient.ResponseSpec postComment(RestTestClient user, String articleId, String content) {
        return user.post().uri(API + "/articles/{id}/comments", articleId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content))
                .exchange();
    }
}
