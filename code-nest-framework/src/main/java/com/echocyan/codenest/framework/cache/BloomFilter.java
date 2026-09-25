package com.echocyan.codenest.framework.cache;

import java.util.ArrayList;
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
 * <p><b>导入完成标记：</b>全量导入完成后才写入 {@code bf:<name>:ready}。过滤器或标记任一不存在时，查询一律视为
 * "可能存在"，也就是不拦截，下次启动时重建。所以导入期间不会误判，导入中断后重启会重新导入；
 * 重复加入同一个 ID 无害，多个实例同时启动时各自导入一遍。{@link #add} 在过滤器不存在时按同样的参数新建，
 * 导入期间创建的对象不会漏掉；新建时同时删除标记，运行期间过滤器丢失也会退回"不拦截"，直到下次启动重建。
 *
 * <p><b>已知局限：</b>Redis 从较早的快照恢复时，过滤器与标记都在，但缺少快照之后创建的 ID，这些对象会被误判为不存在，
 * 需要手动删除标记后重启。
 */
@Slf4j
public class BloomFilter {

    /** 误判率。 */
    private static final String ERROR_RATE = "0.001";

    /** 预计容量，超出后 Redis 自动扩容。 */
    private static final String CAPACITY = "1000000";

    /**
     * KEYS：过滤器、导入完成标记；ARGV：误判率、容量、要加入的 ID。过滤器不存在时先删除标记，再按给定参数新建：
     * 新建的过滤器缺少此前的 ID，要等下次启动时重新导入。
     */
    private static final RedisScript<Long> ADD = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[2])
                redis.call('BF.RESERVE', KEYS[1], ARGV[1], ARGV[2])
            end
            redis.call('BF.MADD', KEYS[1], unpack(ARGV, 3))
            return 1
            """, Long.class);

    /** KEYS：过滤器、导入完成标记；ARGV：ID。任一 key 不存在时视为可能存在。 */
    private static final RedisScript<Long> MIGHT_CONTAIN = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1], KEYS[2]) < 2 then
                return 1
            end
            return redis.call('BF.EXISTS', KEYS[1], ARGV[1])
            """, Long.class);

    private final String key;
    private final String readyKey;
    private final StringRedisTemplate redis;

    BloomFilter(String name, StringRedisTemplate redis) {
        this.key = "bf:" + name;
        this.readyKey = key + ":ready";
        this.redis = redis;
    }

    /**
     * 加入一个 ID。在对象写库的事务里调用即可：事务回滚只会留下一个误判，不影响正确性。
     */
    public void add(long id) {
        addAll(List.of(id));
    }

    /**
     * 过滤器或导入完成标记不存在时，按 ID 游标分批导入全部 ID，完成后写入标记；两者都在时什么也不做。
     *
     * @param idsAfter 返回大于给定 ID 的下一批 ID，按 ID 升序；参数为 null 时从头开始，返回空列表表示结束
     */
    public void rebuildIfAbsent(Function<Long, List<Long>> idsAfter) {
        if (redis.countExistingKeys(List.of(key, readyKey)) == 2) {
            return;
        }
        long imported = 0;
        List<Long> ids = idsAfter.apply(null);
        while (!ids.isEmpty()) {
            addAll(ids);
            imported += ids.size();
            ids = idsAfter.apply(ids.getLast());
        }
        redis.opsForValue().set(readyKey, "1");
        log.info("Rebuilt bloom filter {} with {} ids", key, imported);
    }

    boolean mightContain(long id) {
        return redis.execute(MIGHT_CONTAIN, List.of(key, readyKey), String.valueOf(id)) == 1;
    }

    private void addAll(List<Long> ids) {
        List<String> args = new ArrayList<>(List.of(ERROR_RATE, CAPACITY));
        ids.forEach(id -> args.add(String.valueOf(id)));
        redis.execute(ADD, List.of(key, readyKey), args.toArray());
    }
}
