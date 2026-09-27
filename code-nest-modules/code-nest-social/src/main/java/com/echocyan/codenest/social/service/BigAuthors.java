package com.echocyan.codenest.social.service;

import com.echocyan.codenest.counter.api.IdCount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link FeedStore} 按粉丝数识别大 V：粉丝数不低于阈值的作者。作者跨过阈值时不迁移已推送或未推送的文章，
 * 读 Feed 时按文章 ID 去重。
 *
 * <p>粉丝数存在 ZSet {@code feed:followers}（{@code feed} 是 key 前缀）（member 是作者 ID，score 是粉丝数，没有粉丝的作者不在其中），
 * 关注、取关后由修正消费者按关注表重新统计写入。另有 String {@code feed:followers:ready}：全部重建完成的标记，
 * 启动时不存在就按全部关注关系重建；重建中途失败时标记不会写入，下次启动重来。
 * 读 Feed 时一条 {@code ZRANGEBYSCORE} 取出全部大 V，不必逐个查读者关注的几百个作者的粉丝数。
 * 存的是粉丝数而不是大 V 名单，与阈值无关，阈值不同的实例可以共用。
 *
 * <p>并发修正同一个作者时，后写入的可能是先统计出的旧值；只在粉丝数恰好跨过阈值时影响判定，
 * 到这个作者下次被关注或取关时纠正。
 */
@Slf4j
class BigAuthors {

    /**
     * 重建时每批统计的作者数。
     */
    private static final int REBUILD_BATCH = 1000;

    private final StringRedisTemplate redis;
    private final FollowService followService;
    private final String key;
    private final String readyKey;
    private final long threshold;

    BigAuthors(StringRedisTemplate redis, FollowService followService, String keyPrefix, long threshold) {
        this.redis = redis;
        this.followService = followService;
        this.key = keyPrefix + ":followers";
        this.readyKey = key + ":ready";
        this.threshold = threshold;
    }

    /**
     * @return 给定作者中的大 V
     */
    Set<Long> among(Collection<Long> authorIds) {
        Set<String> big = redis.opsForZSet().rangeByScore(key, threshold, Double.POSITIVE_INFINITY);
        return authorIds.stream()
                .filter(id -> big.contains(String.valueOf(id)))
                .collect(Collectors.toSet());
    }

    boolean isBig(long authorId) {
        Double followers = redis.opsForZSet().score(key, String.valueOf(authorId));
        return followers != null && followers >= threshold;
    }

    /**
     * 按关注表重新统计作者的粉丝数并写入；统计的是绝对值，重复执行结果不变。
     */
    void refresh(long authorId) {
        long followers = followService.countFollowers(authorId);
        if (followers == 0) {
            redis.opsForZSet().remove(key, String.valueOf(authorId));
        } else {
            redis.opsForZSet().add(key, String.valueOf(authorId), followers);
        }
    }

    /**
     * 重建完成标记不存在时（首次部署、Redis 数据丢失，或造数绕过关注事件直接写库），按作者 ID 分批统计全部关注关系
     * 重建，完成后写入标记；多个实例同时启动时各自重建一遍。
     */
    void rebuildIfAbsent() {
        if (Boolean.TRUE.equals(redis.hasKey(readyKey))) {
            return;
        }
        long imported = 0;
        List<IdCount> counts = followService.countFollowersAfter(0, REBUILD_BATCH);
        while (!counts.isEmpty()) {
            Set<TypedTuple<String>> tuples = counts.stream()
                    .map(count -> TypedTuple.of(String.valueOf(count.id()), (double) count.count()))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(key, tuples);
            imported += counts.size();
            counts = followService.countFollowersAfter(counts.getLast().id(), REBUILD_BATCH);
        }
        redis.opsForValue().set(readyKey, "1");
        log.info("Rebuilt follower counts of {} authors for the feed", imported);
    }
}
