package com.echocyan.codenest.framework.cache;

import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 以 ID 为元素的布隆过滤器，用 Redis 8 原生的 {@code BF.*} 命令，key 为 {@code bf:<name>}。
 * 由 {@link TwoLevelCaches#bloomFilter} 创建，交给 {@link TwoLevelCaches#createTwoLevel} 拦截一定不存在的 ID。
 *
 * <p>各档都要维护：对象创建时 {@link #add}，应用启动时 {@link #rebuildIfAbsent}。只在 two-level 档读取，
 * 但只在这一档维护的话，从别的档切过来时过滤器里会缺少期间创建的 ID，把它们误判为不存在。
 *
 * <p><b>过滤器丢失：</b>key 不存在时 {@link #add} 什么也不做，查询一律视为"可能存在"，也就是不拦截，
 * 直到下次启动时重建。{@link #add} 不自动建出只含新 ID 的过滤器，否则重启时会误以为无需重建。
 * 重建期间过滤器已存在但还没导入完，其他实例会把尚未导入的 ID 误判为不存在，只在 Redis 数据丢失后的启动期间出现。
 */
@Slf4j
public class BloomFilter {

    /** 误判率。 */
    private static final String ERROR_RATE = "0.001";

    /** 预计容量，超出后 Redis 自动扩容。 */
    private static final String CAPACITY = "1000000";

    /** KEYS：过滤器；ARGV：误判率、容量。已存在时返回 0。 */
    private static final RedisScript<Long> RESERVE_IF_ABSENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])
            return 1
            """, Long.class);

    /** KEYS：过滤器；ARGV：要加入的 ID。过滤器不存在时什么也不做。 */
    private static final RedisScript<Long> ADD_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('BF.MADD', KEYS[1], unpack(ARGV))
            return 1
            """, Long.class);

    /** KEYS：过滤器；ARGV：ID。过滤器不存在时视为可能存在。 */
    private static final RedisScript<Long> MIGHT_CONTAIN = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 1
            end
            return redis.call('BF.EXISTS', KEYS[1], ARGV[1])
            """, Long.class);

    private final String key;
    private final StringRedisTemplate redis;

    BloomFilter(String name, StringRedisTemplate redis) {
        this.key = "bf:" + name;
        this.redis = redis;
    }

    /**
     * 加入一个 ID。在对象写库的事务里调用即可：事务回滚只会留下一个误判，不影响正确性。
     */
    public void add(long id) {
        addAll(List.of(id));
    }

    /**
     * 过滤器不存在时新建，并按 ID 游标分批导入全部 ID；已存在时什么也不做。多个实例同时启动时只有一个会导入。
     *
     * @param idsAfter 返回大于给定 ID 的下一批 ID，按 ID 升序；参数为 null 时从头开始，返回空列表表示结束
     */
    public void rebuildIfAbsent(Function<Long, List<Long>> idsAfter) {
        if (redis.execute(RESERVE_IF_ABSENT, List.of(key), ERROR_RATE, CAPACITY) == 0) {
            return;
        }
        long imported = 0;
        List<Long> ids = idsAfter.apply(null);
        while (!ids.isEmpty()) {
            addAll(ids);
            imported += ids.size();
            ids = idsAfter.apply(ids.getLast());
        }
        log.info("Rebuilt bloom filter {} with {} ids", key, imported);
    }

    boolean mightContain(long id) {
        return redis.execute(MIGHT_CONTAIN, List.of(key), String.valueOf(id)) == 1;
    }

    private void addAll(List<Long> ids) {
        redis.execute(ADD_IF_PRESENT, List.of(key), ids.stream().map(String::valueOf).toArray());
    }
}
