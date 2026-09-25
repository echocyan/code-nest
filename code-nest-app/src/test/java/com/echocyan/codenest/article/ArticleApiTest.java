package com.echocyan.codenest.article;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;
import java.util.Map;

class ArticleApiTest extends ArticleTestSupport {

    @Test
    void authorCanCreateADraftAndReadItBack() {
        String username = uniqueUsername();
        RestTestClient author = withToken(register(username));

        String id = createDraft(author, draft());

        author.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo(id)
                .jsonPath("$.data.title").isEqualTo("Redis 计数实践")
                .jsonPath("$.data.content").isEqualTo("# 背景\n热点行更新会排队等锁。")
                .jsonPath("$.data.summary").isEqualTo("手写摘要")
                .jsonPath("$.data.coverUrl").isEqualTo("https://example.com/cover.png")
                .jsonPath("$.data.status").isEqualTo("DRAFT")
                .jsonPath("$.data.publishedAt").isEmpty()
                .jsonPath("$.data.category.id").isEqualTo("1")
                .jsonPath("$.data.category.name").isEqualTo("后端")
                .jsonPath("$.data.tags[*].name").isEqualTo(List.of("Java", "Redis"))
                .jsonPath("$.data.author.nickname").isEqualTo(username);
    }

    @Test
    void summaryDefaultsToTheBeginningOfContent() {
        RestTestClient author = withToken(register(uniqueUsername()));
        Map<String, Object> body = draft();
        body.remove("summary");
        body.remove("coverUrl");
        body.remove("tagIds");
        body.put("content", "  " + "字".repeat(150));

        String id = createDraft(author, body);

        author.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectBody()
                .jsonPath("$.data.summary").isEqualTo("字".repeat(100))
                .jsonPath("$.data.coverUrl").isEmpty()
                .jsonPath("$.data.tags").isEmpty();
    }

    @Test
    void draftWithUnknownCategoryOrTagIsRejected() {
        RestTestClient author = withToken(register(uniqueUsername()));
        Map<String, Object> unknownCategory = draft();
        unknownCategory.put("categoryId", 999);
        Map<String, Object> unknownTag = draft();
        unknownTag.put("tagIds", List.of(1, 999));

        expectBadRequest(author, unknownCategory, 20003);
        expectBadRequest(author, unknownTag, 20004);
    }

    @Test
    void draftIsValidated() {
        RestTestClient author = withToken(register(uniqueUsername()));
        Map<String, Object> sixTags = draft();
        sixTags.put("tagIds", List.of(1, 2, 3, 4, 5, 6));
        Map<String, Object> blankTitle = draft();
        blankTitle.put("title", " ");
        Map<String, Object> noCategory = draft();
        noCategory.remove("categoryId");
        Map<String, Object> scriptCover = draft();
        scriptCover.put("coverUrl", "javascript:alert(1)");

        for (Map<String, Object> body : List.of(sixTags, blankTitle, noCategory, scriptCover)) {
            expectBadRequest(author, body, 90400);
        }
    }

    @Test
    void draftIsNotFoundForAnyoneButItsAuthor() {
        String id = createDraft(withToken(register(uniqueUsername())), draft());
        RestTestClient stranger = withToken(register(uniqueUsername()));

        for (RestTestClient viewer : List.of(client, stranger)) {
            expectArticleNotFound(viewer, id);
        }
    }

    @Test
    void publishedArticleIsVisibleToAnyone() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = createDraft(author, draft());
        // 先读一次草稿，发布后详情仍要立即反映新状态
        author.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        author.post().uri(API + "/articles/{id}/publish", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.version").isEqualTo(1);

        client.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.status").isEqualTo("PUBLISHED")
                .jsonPath("$.data.publishedAt").isNotEmpty();
    }

    @Test
    void everyViewOfAPublishedArticleCountsOnce() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, draft()));

        client.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        author.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectBody()
                .jsonPath("$.data.counts.viewCount").isEqualTo(2)
                .jsonPath("$.data.counts.likeCount").isEqualTo(0)
                .jsonPath("$.data.counts.favoriteCount").isEqualTo(0)
                .jsonPath("$.data.counts.commentCount").isEqualTo(0);
    }

    @Test
    void authorsArticleCountTracksPublishedArticles() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String authorId = authorIdOf(author, createDraft(author, draft()));
        String first = publish(author, createDraft(author, draft()));
        publish(author, first);
        String second = publish(author, createDraft(author, draft()));
        expectArticleCount(authorId, 2);

        author.delete().uri(API + "/articles/{id}", second).exchange().expectStatus().isOk();

        expectArticleCount(authorId, 1);
    }

    private void expectArticleCount(String userId, int expected) {
        eventually(() -> client.get().uri(API + "/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.counts.articleCount").isEqualTo(expected)
                .jsonPath("$.data.counts.followerCount").isEqualTo(0)
                .jsonPath("$.data.counts.followingCount").isEqualTo(0)
                .jsonPath("$.data.counts.likeReceivedCount").isEqualTo(0));
    }

    @Test
    void onlyTheAuthorCanPublish() {
        String id = createDraft(withToken(register(uniqueUsername())), draft());

        withToken(register(uniqueUsername())).post().uri(API + "/articles/{id}/publish", id)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(90403);
    }

    @Test
    void authorCanEditAnArticleWithTheCurrentVersion() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = createDraft(author, draft());
        Map<String, Object> edited = draft();
        edited.put("title", "Redis 计数实践（修订）");
        edited.put("tagIds", List.of(13));
        edited.remove("coverUrl");
        edited.put("content", "修订后的正文");
        // 先读一次旧内容，编辑后详情仍要立即是新内容
        author.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        edit(author, id, 0, edited)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.version").isEqualTo(1);

        author.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectBody()
                .jsonPath("$.data.title").isEqualTo("Redis 计数实践（修订）")
                .jsonPath("$.data.content").isEqualTo("修订后的正文")
                .jsonPath("$.data.coverUrl").isEmpty()
                .jsonPath("$.data.tags[*].name").isEqualTo(List.of("高并发"))
                .jsonPath("$.data.version").isEqualTo(1);
    }

    @Test
    void editWithAStaleVersionConflicts() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = createDraft(author, draft());
        edit(author, id, 0, draft()).expectStatus().isOk();

        edit(author, id, 0, draft())
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo(20002);
    }

    @Test
    void onlyTheAuthorCanEdit() {
        String id = createDraft(withToken(register(uniqueUsername())), draft());

        edit(withToken(register(uniqueUsername())), id, 0, draft())
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(90403);
    }

    @Test
    void deletedArticleIsNotFound() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, draft()));
        client.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        author.delete().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isOk();

        expectArticleNotFound(author, id);
        expectArticleNotFound(client, id);
    }

    @Test
    void authorNicknameFollowsProfileEdits() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, draft()));
        client.get().uri(API + "/articles/{id}", id).exchange().expectStatus().isOk();

        author.put().uri(API + "/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("nickname", "改名后的作者"))
                .exchange()
                .expectStatus().isOk();

        client.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectBody().jsonPath("$.data.author.nickname").isEqualTo("改名后的作者");
    }

    @Test
    void onlyTheAuthorCanDelete() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String id = publish(author, createDraft(author, draft()));

        withToken(register(uniqueUsername())).delete().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo(90403);
        client.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unknownArticleIsNotFoundEveryTime() {
        for (int i = 0; i < 3; i++) {
            expectArticleNotFound(client, "1");
        }
    }

    private void expectArticleNotFound(RestTestClient viewer, String id) {
        viewer.get().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo(20001);
    }

    private void expectBadRequest(RestTestClient author, Map<String, Object> body, int code) {
        author.post().uri(API + "/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo(code);
    }
}
