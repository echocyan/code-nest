package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.HotArticleService;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 榜单存在 ZSet {@code hot:articles} 中，member 为文章 ID，score 为热度。重算时先写入临时 key，
 * 再用 {@code RENAME} 原子替换，读取方看不到写了一半的榜单。已删除的文章在读取时滤掉，下一轮重算时自然不会进入候选集。
 */
@Slf4j
@Service
class HotArticleServiceImpl implements HotArticleService {

    private static final String KEY = "hot:articles";

    private static final String TMP_KEY = "hot:articles:tmp";

    private static final String LOCK_KEY = "hot:lock";

    /** 锁的过期时间，短于重算间隔；实例崩溃时锁最迟在这之后释放，不影响下一轮。 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    /** 候选集：最近这段时间内发布的文章。 */
    private static final Duration CANDIDATE_WINDOW = Duration.ofDays(7);

    /** 一次批量读取计数的文章数。 */
    private static final int BATCH = 500;

    /** KEYS：锁；ARGV：加锁时写入的 token。只释放自己持有的锁。 */
    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final ArticleApi articleApi;
    private final ArticleService articleService;
    private final CounterApi counterApi;
    private final StringRedisTemplate redis;
    private final HotFormula formula;

    HotArticleServiceImpl(ArticleApi articleApi, ArticleService articleService, CounterApi counterApi,
                          StringRedisTemplate redis,
                          @Value("${hot.weight.like}") double likeWeight,
                          @Value("${hot.weight.favorite}") double favoriteWeight,
                          @Value("${hot.weight.comment}") double commentWeight,
                          @Value("${hot.weight.view}") double viewWeight,
                          @Value("${hot.gravity}") double gravity) {
        this.articleApi = articleApi;
        this.articleService = articleService;
        this.counterApi = counterApi;
        this.redis = redis;
        this.formula = new HotFormula(likeWeight, favoriteWeight, commentWeight, viewWeight, gravity);
    }

    /**
     * 每 5 分钟整点执行一次，各实例同时触发，由锁保证只有一个实例在算。
     */
    @Scheduled(cron = "0 */5 * * * *")
    void scheduledRefresh() {
        refresh();
    }

    @Override
    public void refresh() {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(LOCK_KEY, token, LOCK_TTL))) {
            log.debug("Hot list is being refreshed on another instance, skipped");
            return;
        }
        try {
            Set<TypedTuple<String>> top = top(DateTimes.now());
            if (top.isEmpty()) {
                redis.delete(KEY);
                return;
            }
            redis.delete(TMP_KEY);
            redis.opsForZSet().add(TMP_KEY, top);
            redis.rename(TMP_KEY, KEY);
        } finally {
            redis.execute(UNLOCK, List.of(LOCK_KEY), token);
        }
    }

    @Override
    public PageResult<ArticleItemVO> page(int page) {
        long start = (long) (page - 1) * PAGE_SIZE;
        Set<String> members = redis.opsForZSet().reverseRange(KEY, start, start + PAGE_SIZE - 1);
        Long total = redis.opsForZSet().zCard(KEY);
        List<Long> ids = members == null ? List.of() : members.stream().map(Long::valueOf).toList();
        return new PageResult<>(articleService.listPublishedItems(ids), total == null ? 0 : total, page, PAGE_SIZE);
    }

    /**
     * 按热度取候选集的前 {@value #CAPACITY} 名。
     */
    private Set<TypedTuple<String>> top(LocalDateTime now) {
        Map<Long, LocalDateTime> candidates = articleApi.getPublishedSince(now.minus(CANDIDATE_WINDOW));
        List<Long> ids = List.copyOf(candidates.keySet());
        List<TypedTuple<String>> scored = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            Map<Long, Counts> counts = counterApi.get(CounterTarget.ARTICLE, batch);
            for (Long id : batch) {
                double hours = Math.max(0, Duration.between(candidates.get(id), now).toMillis() / 3_600_000.0);
                scored.add(TypedTuple.of(String.valueOf(id), formula.score(counts.get(id), hours)));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparing(TypedTuple<String>::getScore).reversed())
                .limit(CAPACITY)
                .collect(Collectors.toSet());
    }
}
