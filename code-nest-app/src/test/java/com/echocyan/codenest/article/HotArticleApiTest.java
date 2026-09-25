package com.echocyan.codenest.article;

import static org.assertj.core.api.Assertions.assertThat;

import com.echocyan.codenest.article.service.HotArticleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 热榜：手动触发一次重算后匿名读取。所有测试共用一个库，榜单里还有其他测试的文章，只断言本测试文章之间的相对位置；
 * 前提是其他测试留下的、比"刚发布且有一个赞"更热的文章不足 100 篇。
 */
class HotArticleApiTest extends ArticleTestSupport {

    @Autowired
    private HotArticleService hotArticleService;

    @Test
    void articlesWithMoreInteractionsRankHigher() {
        RestTestClient author = withToken(register(uniqueUsername()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient other = withToken(register(uniqueUsername()));
        String cold = publish(author, createDraft(author, draft()));
        String hot = publish(author, createDraft(author, draft()));
        reader.put().uri(API + "/articles/{id}/like", cold).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/like", hot).exchange().expectStatus().isOk();
        other.put().uri(API + "/articles/{id}/like", hot).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/favorite", hot).exchange().expectStatus().isOk();
        reader.post().uri(API + "/articles/{id}/comments", hot)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "好文"))
                .exchange()
                .expectStatus().isOk();

        // 计数可能异步生效，重算也可能恰好被其他上下文的定时任务占住锁，反复重算直到榜单反映出计数
        eventually(() -> {
            hotArticleService.refresh();
            assertThat(readHotList()).containsSubsequence(hot, cold);
        });
        String item = "$.data.list[?(@.id == '%s')]".formatted(hot);
        client.get().uri(API + "/hot-articles?page={page}", pageOf(hot))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath(item + ".summary").isEqualTo(List.of("手写摘要"))
                .jsonPath(item + ".counts.likeCount").isEqualTo(List.of(2))
                .jsonPath(item + ".counts.favoriteCount").isEqualTo(List.of(1))
                .jsonPath(item + ".counts.commentCount").isEqualTo(List.of(1));
    }

    @Test
    void deletedArticleDisappearsWithoutRefresh() {
        RestTestClient author = withToken(register(uniqueUsername()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        String article = publish(author, createDraft(author, draft()));
        reader.put().uri(API + "/articles/{id}/favorite", article).exchange().expectStatus().isOk();
        reader.put().uri(API + "/articles/{id}/like", article).exchange().expectStatus().isOk();
        eventually(() -> {
            hotArticleService.refresh();
            assertThat(readHotList()).contains(article);
        });

        delete(author, article);

        assertThat(readHotList()).doesNotContain(article);
    }

    @Test
    void pageIsLimitedToFive() {
        client.get().uri(API + "/hot-articles?page=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.size").isEqualTo(20);
        for (int page : new int[]{0, 6}) {
            client.get().uri(API + "/hot-articles?page={page}", page)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo(90400);
        }
    }

    /**
     * 按页码依次读出全部 5 页热榜的文章 ID。
     */
    private List<String> readHotList() {
        List<String> ids = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            ids.addAll(readPage(page));
        }
        return ids;
    }

    private int pageOf(String articleId) {
        for (int page = 1; page <= 5; page++) {
            if (readPage(page).contains(articleId)) {
                return page;
            }
        }
        throw new AssertionError("文章不在热榜上: " + articleId);
    }

    @SuppressWarnings("unchecked")
    private List<String> readPage(int page) {
        List<String> ids = new ArrayList<>();
        client.get().uri(API + "/hot-articles?page={page}", page)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.list[*].id").value(List.class, ids::addAll);
        return ids;
    }
}
