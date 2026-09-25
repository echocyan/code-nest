package com.echocyan.codenest.article;

import com.echocyan.codenest.support.IntegrationTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文章相关 HTTP 测试共用的造数步骤，都走真实接口。
 */
public abstract class ArticleTestSupport extends IntegrationTest {

    /**
     * 一份合法的草稿请求体；分类 1 = 后端，标签 1 = Java、6 = Redis。
     */
    protected static Map<String, Object> draft() {
        return new HashMap<>(Map.of(
                "title", "Redis 计数实践",
                "content", "# 背景\n热点行更新会排队等锁。",
                "summary", "手写摘要",
                "coverUrl", "https://example.com/cover.png",
                "categoryId", 1,
                "tagIds", List.of(1, 6)));
    }

    /**
     * 指定分类和标签的草稿请求体。
     */
    protected static Map<String, Object> draftIn(int categoryId, int tagId) {
        Map<String, Object> body = draft();
        body.put("categoryId", categoryId);
        body.put("tagIds", List.of(tagId));
        return body;
    }

    /**
     * 新建草稿，返回文章 ID。
     */
    protected String createDraft(RestTestClient author, Map<String, Object> body) {
        AtomicReference<String> id = new AtomicReference<>();
        author.post().uri(API + "/articles")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.id").value(String.class, id::set);
        return id.get();
    }

    /**
     * 发布文章，返回传入的文章 ID，便于和 {@link #createDraft} 串起来用。
     */
    protected String publish(RestTestClient author, String id) {
        author.post().uri(API + "/articles/{id}/publish", id)
                .exchange()
                .expectStatus().isOk();
        return id;
    }

    /**
     * 编辑文章，version 为读到的版本号：新建的草稿是 0，发布后是 1。
     */
    protected RestTestClient.ResponseSpec edit(RestTestClient author, String id, int version,
                                               Map<String, Object> body) {
        return author.put().uri(API + "/articles/{id}?version={version}", id, version)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    protected void delete(RestTestClient author, String id) {
        author.delete().uri(API + "/articles/{id}", id)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * 从文章详情读出作者 ID。
     */
    protected String authorIdOf(RestTestClient author, String articleId) {
        AtomicReference<String> authorId = new AtomicReference<>();
        author.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectBody().jsonPath("$.data.author.id").value(String.class, authorId::set);
        return authorId.get();
    }
}
