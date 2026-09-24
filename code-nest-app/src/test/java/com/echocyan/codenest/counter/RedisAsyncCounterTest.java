package com.echocyan.codenest.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.echocyan.codenest.article.ArticleTestSupport;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.support.RedisAsyncCounter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * redis-async 档的消费、落库与懒加载。落库每 5 秒一次，等待落库的断言留足时间。
 */
@RedisAsyncCounter
class RedisAsyncCounterTest extends ArticleTestSupport {

    private static final Duration FLUSHED = Duration.ofSeconds(20);

    @Autowired
    private CounterApi counterApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void flushedCountsMatchTheRelationTable() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publish(author, createDraft(author, draft()));
        String authorId = authorIdOf(author, articleId);
        List<RestTestClient> readers = List.of(reader(), reader(), reader());
        readers.forEach(reader -> like(reader, articleId));
        readers.getFirst().delete().uri(API + "/articles/{id}/like", articleId).exchange().expectStatus().isOk();

        await().atMost(FLUSHED).untilAsserted(() -> {
            assertThat(stat("article_stat", "like_count", "article_id", articleId))
                    .isEqualTo(count("SELECT COUNT(*) FROM article_like WHERE article_id = ?", articleId))
                    .isEqualTo(2);
            assertThat(stat("user_stat", "like_received_count", "user_id", authorId)).isEqualTo(2);
        });
    }

    @Test
    void duplicateMessageIsCountedOnce() {
        long duplicated = randomId();
        long marker = randomId();
        String messageId = UUID.randomUUID().toString();

        send(messageId, duplicated);
        send(messageId, duplicated);
        send(UUID.randomUUID().toString(), marker);

        // 单个消费者按顺序处理，marker 生效时重复消息也已处理过
        await().atMost(Duration.ofSeconds(10)).until(() -> replies(marker) == 1);
        assertThat(replies(duplicated)).isEqualTo(1);
    }

    @Test
    void countsAreLazilyRestoredAfterRedisLosesThem() {
        RestTestClient author = withToken(register(uniqueUsername()));
        String articleId = publish(author, createDraft(author, draft()));
        String authorId = authorIdOf(author, articleId);
        like(reader(), articleId);
        like(reader(), articleId);
        await().atMost(FLUSHED).until(() -> stat("article_stat", "like_count", "article_id", articleId) == 2
                && stat("user_stat", "like_received_count", "user_id", authorId) == 2);

        redis.delete(List.of("counter:article:" + articleId, "counter:user:" + authorId));

        // 读取时从 MySQL 回填
        expectLikes(articleId, 2);
        redis.delete(List.of("counter:article:" + articleId, "counter:user:" + authorId));
        // 消费时从 MySQL 回填，再累加
        like(reader(), articleId);
        eventually(() -> expectLikes(articleId, 3));
        eventually(() -> client.get().uri(API + "/users/{id}", authorId)
                .exchange()
                .expectBody().jsonPath("$.data.counts.likeReceivedCount").isEqualTo(3));
    }

    @Test
    void resetFixesBothRedisAndMysql() {
        long commentId = randomId();
        counterApi.reset(CounterMetric.COMMENT_REPLY, commentId, 5);
        assertThat(replies(commentId)).isEqualTo(5);

        counterApi.reset(CounterMetric.COMMENT_REPLY, commentId, 3);

        assertThat(replies(commentId)).isEqualTo(3);
        assertThat(stat("comment_stat", "reply_count", "comment_id", String.valueOf(commentId))).isEqualTo(3);
    }

    private RestTestClient reader() {
        return withToken(register(uniqueUsername()));
    }

    private void like(RestTestClient reader, String articleId) {
        reader.put().uri(API + "/articles/{id}/like", articleId).exchange().expectStatus().isOk();
    }

    private void expectLikes(String articleId, int expected) {
        client.get().uri(API + "/articles/{id}", articleId)
                .exchange()
                .expectBody().jsonPath("$.data.counts.likeCount").isEqualTo(expected);
    }

    private long replies(long commentId) {
        return counterApi.get(CounterTarget.COMMENT, List.of(commentId)).get(commentId).get(CounterMetric.COMMENT_REPLY);
    }

    private long stat(String table, String column, String idColumn, String id) {
        List<Long> values = jdbcTemplate.queryForList(
                "SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = ?", Long.class, id);
        return values.isEmpty() ? 0 : values.getFirst();
    }

    private long count(String sql, String id) {
        return jdbcTemplate.queryForObject(sql, Long.class, id);
    }

    /**
     * 经默认交换机直接投递一条回复数 +1 的消息，消息格式与 DomainEventPublisher 发出的一致。
     */
    private void send(String messageId, long commentId) {
        String body = "{\"metric\":\"COMMENT_REPLY\",\"targetId\":%d,\"delta\":1}".formatted(commentId);
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .setType(CounterChangedEvent.class.getName())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send("", "counter.update", message);
    }

    /** 不存在于任何表中的对象 ID，计数从 0 开始。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(1L << 60, Long.MAX_VALUE);
    }
}
