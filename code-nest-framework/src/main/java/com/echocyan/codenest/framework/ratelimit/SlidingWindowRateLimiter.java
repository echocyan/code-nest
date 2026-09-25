package com.echocyan.codenest.framework.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口日志限流：每个额度一个 ZSet，member 是请求 ID，score 是请求时间（毫秒）。
 * 时间取自 Redis {@code TIME}，多个应用实例之间的时钟偏差不影响判断。
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";

    /**
     * KEYS：各额度的 ZSet；ARGV：请求 ID，再依次为各额度的限额和窗口毫秒数。
     * 先清掉所有额度窗口外的记录并计数，全部有余量才给每个额度记下本次请求；
     * 否则什么都不记，返回最晚腾出名额的那个额度还要等待的毫秒数：通常就是等窗口中最早一条记录滑出。
     */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local wait = 0
            for i, key in ipairs(KEYS) do
                local limit = tonumber(ARGV[i * 2])
                local window = tonumber(ARGV[i * 2 + 1])
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
                local count = redis.call('ZCARD', key)
                if count >= limit then
                    -- 调低限额后窗口内可能多于 limit 条，要等到只剩 limit - 1 条才有名额
                    local freeing = redis.call('ZRANGE', key, count - limit, count - limit, 'WITHSCORES')
                    wait = math.max(wait, tonumber(freeing[2]) + window - now)
                end
            end
            if wait > 0 then
                return wait
            end
            for i, key in ipairs(KEYS) do
                redis.call('ZADD', key, now, ARGV[1])
                redis.call('PEXPIRE', key, ARGV[i * 2 + 1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 原子地检查一组额度：全部有余量时给每个额度记一次请求。
     *
     * @return 0 表示放行；否则为被拒绝后至少要等待的毫秒数
     * @throws org.springframework.dao.DataAccessException Redis 不可用
     */
    public long tryAcquire(List<Quota> quotas) {
        List<String> keys = new ArrayList<>(quotas.size());
        List<String> args = new ArrayList<>(quotas.size() * 2 + 1);
        args.add(UUID.randomUUID().toString());
        for (Quota quota : quotas) {
            keys.add(KEY_PREFIX + quota.key());
            args.add(String.valueOf(quota.limit()));
            args.add(String.valueOf(quota.window().toMillis()));
        }
        return redis.execute(ACQUIRE, keys, args.toArray());
    }

    /**
     * 一个额度：{@code key} 在 {@code window} 内最多 {@code limit} 次请求。
     */
    public record Quota(String key, int limit, Duration window) {
    }
}
