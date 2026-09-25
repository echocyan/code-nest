package com.echocyan.codenest.framework.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 多实例之间互斥执行任务的 Redis 锁：{@code SET key token NX PX ttl} 加锁，任务结束后只释放自己持有的锁。
 * 不续期：任务超过 ttl 后锁自动过期，其他实例可能同时执行，所以 ttl 要长于任务耗时；实例崩溃时锁最迟在 ttl 后释放。
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    /** KEYS：锁；ARGV：加锁时写入的 token。 */
    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 抢到锁就执行任务，无论正常结束还是抛出异常都释放锁。
     *
     * @param task 返回值不能为 null
     * @return 任务的结果；锁被其他持有者占着时为空，任务不执行
     */
    public <T> Optional<T> tryRun(String key, Duration ttl, Supplier<T> task) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl))) {
            return Optional.empty();
        }
        try {
            return Optional.of(task.get());
        } finally {
            redis.execute(UNLOCK, List.of(key), token);
        }
    }

    /**
     * 同 {@link #tryRun(String, Duration, Supplier)}，用于没有结果的任务。
     *
     * @return 是否抢到锁并执行了任务
     */
    public boolean tryRun(String key, Duration ttl, Runnable task) {
        return tryRun(key, ttl, () -> {
            task.run();
            return true;
        }).isPresent();
    }
}
