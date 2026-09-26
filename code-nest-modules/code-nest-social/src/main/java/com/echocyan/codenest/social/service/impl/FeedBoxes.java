package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.article.api.ArticleState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisZSetCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 推拉结合 Feed 的 Redis 存储。
 *
 * <p>key 设计，都是 ZSet，member 与 score 都是文章 ID：
 * <ul>
 *     <li>{@code feed:outbox:{authorId}}：作者的发件箱，最近发布的 {@value #OUTBOX_CAP} 篇文章，不设 TTL。</li>
 *     <li>{@code feed:inbox:{userId}}：读者的收件箱，推送来的普通作者的文章，至多 {@value #INBOX_CAP} 条，
 *     TTL 7 天、读取时续期。key 不存在说明读者 7 天没来过：推送时跳过，读取时从发件箱重建。</li>
 * </ul>
 * 另有 String {@code feed:outbox:ready}：发件箱全部重建完成的标记，见 {@link FeedFanoutServiceImpl#rebuildOutboxesIfAbsent}。
 * score 是 double，大于 2^53 的雪花 ID 转换时会舍入，相邻的 ID 可能得到相同的 score。score 相同的 member 按字典序排列，
 * 位数相同的 ID 字典序就是数值序，所以 ZSet 内的顺序仍与 ID 一致；按游标读取时，与游标 score 相同的那一组单独取出，
 * 在 Java 里按 ID 精确比较。
 */
@Component
@RequiredArgsConstructor
class FeedBoxes {

    static final int OUTBOX_CAP = 100;

    static final int INBOX_CAP = 500;

    private static final Duration INBOX_TTL = Duration.ofDays(7);

    private static final String OUTBOX_READY = "feed:outbox:ready";

    /**
     * 只保留最新的 ARGV[1] 条：ZSet 按 score 升序，最旧的排在前面。各脚本的 ARGV[1] 都是上限。
     */
    private static final String TRIM = """
            redis.call('ZREMRANGEBYRANK', KEYS[1], 0, -tonumber(ARGV[1]) - 1)
            """;

    /**
     * 把发件箱 src 的全部文章并入 KEYS[1] 并裁剪；ZADD 不改变 KEYS[1] 的 TTL。
     */
    private static final String MERGE = """
            local function merge(src)
                local items = redis.call('ZRANGE', src, 0, -1, 'WITHSCORES')
                if #items == 0 then
                    return
                end
                local args = {}
                for i = 1, #items, 2 do
                    args[#args + 1] = items[i + 1]
                    args[#args + 1] = items[i]
                end
                redis.call('ZADD', KEYS[1], unpack(args))
            """ + TRIM + """
            end
            """;

    /**
     * KEYS：ZSet；ARGV：上限、文章 ID。
     */
    private static final RedisScript<Long> ADD = RedisScript.of("""
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            """ + TRIM + """
            return 1
            """, Long.class);

    private static final byte[] ADD_BYTES = ADD.getScriptAsString().getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS：收件箱；ARGV：上限、文章 ID。收件箱存在时才写入，不会造出没有 TTL 的收件箱。
     */
    private static final byte[] ADD_IF_PRESENT = ("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2])
            """ + TRIM + """
            return 1
            """).getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS：收件箱、发件箱；ARGV：上限。收件箱存在时才并入。
     */
    private static final RedisScript<Long> MERGE_IF_PRESENT = RedisScript.of(MERGE + """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            merge(KEYS[2])
            return 1
            """, Long.class);

    /**
     * KEYS：收件箱、各发件箱；ARGV：上限、TTL 秒数。发件箱都为空时收件箱仍不存在。
     */
    private static final RedisScript<Long> REBUILD = RedisScript.of(MERGE + """
            for i = 2, #KEYS do
                merge(KEYS[i])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    /**
     * KEYS：收件箱、发件箱。从收件箱中移除发件箱里的全部文章。
     */
    private static final RedisScript<Long> REMOVE_ALL_OF = RedisScript.of("""
            local ids = redis.call('ZRANGE', KEYS[2], 0, -1)
            if #ids == 0 then
                return 0
            end
            return redis.call('ZREM', KEYS[1], unpack(ids))
            """, Long.class);

    private final StringRedisTemplate redis;

    private static String outboxKey(long authorId) {
        return "feed:outbox:" + authorId;
    }

    private static String inboxKey(long userId) {
        return "feed:inbox:" + userId;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    void addToOutbox(long authorId, long articleId) {
        redis.execute(ADD, List.of(outboxKey(authorId)), String.valueOf(OUTBOX_CAP), String.valueOf(articleId));
    }

    /**
     * 用 pipeline 把一批文章写入各自作者的发件箱。
     */
    void addToOutboxes(Collection<ArticleState> articles) {
        byte[] cap = bytes(String.valueOf(OUTBOX_CAP));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            articles.forEach(article -> connection.scriptingCommands().eval(ADD_BYTES, ReturnType.INTEGER, 1,
                    bytes(outboxKey(article.authorId())), cap, bytes(String.valueOf(article.id()))));
            return null;
        });
    }

    boolean outboxesReady() {
        return Boolean.TRUE.equals(redis.hasKey(OUTBOX_READY));
    }

    void markOutboxesReady() {
        redis.opsForValue().set(OUTBOX_READY, "1");
    }

    void removeFromOutbox(long authorId, long articleId) {
        redis.opsForZSet().remove(outboxKey(authorId), String.valueOf(articleId));
    }

    /**
     * 用 pipeline 把文章推送给一批读者，收件箱不存在的读者跳过。
     */
    void pushToInboxes(Collection<Long> userIds, long articleId) {
        byte[] id = bytes(String.valueOf(articleId));
        byte[] cap = bytes(String.valueOf(INBOX_CAP));
        redis.executePipelined((RedisCallback<Object>) connection -> {
            userIds.forEach(userId -> connection.scriptingCommands()
                    .eval(ADD_IF_PRESENT, ReturnType.INTEGER, 1, bytes(inboxKey(userId)), cap, id));
            return null;
        });
    }

    /**
     * 读者的收件箱存在时，把作者发件箱里的文章并入。
     */
    void mergeOutboxIntoInbox(long userId, long authorId) {
        redis.execute(MERGE_IF_PRESENT, List.of(inboxKey(userId), outboxKey(authorId)), String.valueOf(INBOX_CAP));
    }

    /**
     * 从读者的收件箱中移除作者发件箱里的文章；更早的、已不在发件箱里的留在收件箱中。
     */
    void removeOutboxFromInbox(long userId, long authorId) {
        redis.execute(REMOVE_ALL_OF, List.of(inboxKey(userId), outboxKey(authorId)));
    }

    /**
     * 收件箱存在时续期；不存在时用这些作者的发件箱重建，只保留最新的 {@value #INBOX_CAP} 条。
     */
    void renewOrRebuildInbox(long userId, Collection<Long> authorIds) {
        String inbox = inboxKey(userId);
        if (Boolean.TRUE.equals(redis.expire(inbox, INBOX_TTL)) || authorIds.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(List.of(inbox));
        authorIds.forEach(authorId -> keys.add(outboxKey(authorId)));
        redis.execute(REBUILD, keys, String.valueOf(INBOX_CAP), String.valueOf(INBOX_TTL.toSeconds()));
    }

    /**
     * 用 pipeline 读出收件箱与各作者发件箱中 ID 小于游标的文章，每个 ZSet 取最新的 limit 条。
     *
     * @param cursor 为 null 时从最新的开始
     * @return 各 ZSet 结果的合集，未排序、未去重
     */
    List<Long> readBefore(long userId, Collection<Long> authorIds, Long cursor, int limit) {
        List<byte[]> keys = Stream.concat(Stream.of(inboxKey(userId)), authorIds.stream().map(FeedBoxes::outboxKey))
                .map(FeedBoxes::bytes)
                .toList();
        List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
            RedisZSetCommands zSet = connection.zSetCommands();
            for (byte[] key : keys) {
                if (cursor == null) {
                    zSet.zRevRange(key, 0, limit - 1);
                } else {
                    // 与游标 score 相同的一组可能有比游标大的 ID，取出后再精确过滤；其余的 score 更小，ID 一定小于游标
                    double score = cursor;
                    zSet.zRangeByScore(key, score, score);
                    zSet.zRevRangeByScore(key, Range.leftUnbounded(Range.Bound.exclusive(score)),
                            Limit.limit().count(limit));
                }
            }
            return null;
        });
        List<Long> ids = new ArrayList<>();
        for (Object reply : replies) {
            for (Object member : (Set<?>) reply) {
                long id = Long.parseLong((String) member);
                if (cursor == null || id < cursor) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
