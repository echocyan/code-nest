package com.echocyan.codenest.social;

import com.echocyan.codenest.social.service.FeedFanoutService;
import com.echocyan.codenest.support.PushPullFeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在 push-pull 档下运行 {@link FeedApiTest} 的全部测试，另外覆盖推拉结合特有的场景。本档有 2 个粉丝即为大 V。
 */
@PushPullFeed
class FeedPushPullApiTest extends FeedApiTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private FeedFanoutService feedFanoutService;

    @Test
    void articlesOfBigAndNormalAuthorsAreMergedNewestFirst() {
        RestTestClient big = withToken(register(uniqueUsername()));
        RestTestClient normal = withToken(register(uniqueUsername()));
        List<String> newestFirst = new ArrayList<>();
        newestFirst.addFirst(publish(normal, createDraft(normal, draft())));
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient fan = withToken(register(uniqueUsername()));
        follow(reader, big);
        follow(fan, big);
        follow(reader, normal);
        // 先读一次，让读者的收件箱存在，之后普通作者的新文章经推送进入收件箱
        eventually(() -> assertThat(readAllPages(reader, 20)).isEqualTo(newestFirst));

        for (RestTestClient author : List.of(big, normal, big, normal)) {
            newestFirst.addFirst(publish(author, createDraft(author, draft())));
        }

        eventually(() -> assertThat(readAllPages(reader, 2)).isEqualTo(newestFirst));
    }

    @Test
    void expiredInboxIsRebuiltOnRead() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String older = publish(author, createDraft(author, draft()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        follow(reader, author);
        eventually(() -> assertThat(readAllPages(reader, 20)).containsExactly(older));

        // 模拟读者 7 天没来、收件箱已过期：这期间发布的文章不会推送给他
        redis.delete("feed:inbox:" + idOf(reader));
        String newer = publish(author, createDraft(author, draft()));

        eventually(() -> assertThat(readAllPages(reader, 20)).containsExactly(newer, older));
    }

    @Test
    void historyOfNewlyFollowedAuthorShowsUp() {
        RestTestClient newcomer = withToken(register(uniqueUsername()));
        String first = publish(newcomer, createDraft(newcomer, draft()));
        String second = publish(newcomer, createDraft(newcomer, draft()));
        RestTestClient followed = withToken(register(uniqueUsername()));
        String latest = publish(followed, createDraft(followed, draft()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        follow(reader, followed);
        eventually(() -> assertThat(readAllPages(reader, 20)).containsExactly(latest));

        follow(reader, newcomer);

        eventually(() -> assertThat(readAllPages(reader, 20)).containsExactly(latest, second, first));
    }

    @Test
    void lostOutboxesAreRebuiltOnRestart() {
        RestTestClient big = withToken(register(uniqueUsername()));
        RestTestClient normal = withToken(register(uniqueUsername()));
        RestTestClient reader = withToken(register(uniqueUsername()));
        RestTestClient fan = withToken(register(uniqueUsername()));
        follow(reader, big);
        follow(fan, big);
        follow(reader, normal);
        String bigArticle = publish(big, createDraft(big, draft()));
        String normalArticle = publish(normal, createDraft(normal, draft()));
        eventually(() -> assertThat(readAllPages(reader, 20)).containsExactly(normalArticle, bigArticle));

        // 模拟 Redis 数据丢失（或绕过发文事件直接写库的造数），再模拟重启：各实例启动时都会调用 rebuildOutboxesIfAbsent
        redis.delete(List.of("feed:outbox:ready", "feed:outbox:" + idOf(big), "feed:outbox:" + idOf(normal),
                "feed:inbox:" + idOf(reader)));
        feedFanoutService.rebuildOutboxesIfAbsent();

        assertThat(readAllPages(reader, 20)).containsExactly(normalArticle, bigArticle);
    }

    private void follow(RestTestClient follower, RestTestClient author) {
        follower.put().uri(API + "/users/{id}/follow", idOf(author)).exchange().expectStatus().isOk();
    }
}
