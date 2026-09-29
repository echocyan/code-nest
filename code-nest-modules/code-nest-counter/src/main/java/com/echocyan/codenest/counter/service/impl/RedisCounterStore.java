package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 计数存储，MySQL 计数表是它的持久副本。
 *
 * <p>key 设计：
 * <ul>
 *     <li>{@code counter:{type}:{id}}：一个对象的全部计数，Hash，字段名见 {@link CounterTables#field}，不设 TTL。
 *     新增指标后，已有的 Hash 里没有新字段，按缺失处理。</li>
 *     <li>{@code counter:dirty:{type}}：待落库的对象 ID，Set。</li>
 *     <li>{@code counter:dedup:{messageId}}：消费去重标记，24 小时过期。</li>
 * </ul>
 * Hash 或其中的字段不存在时一律先从 MySQL 回填、且只写入仍缺失的字段，不能直接累加，否则会从 0 开始把已有计数冲掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisCounterStore {

    /**
     * 每次 {@code SPOP} 取出的对象数上限，也是一条批量写入语句的行数上限。
     */
    static final int FLUSH_BATCH = 1000;

    /**
     * 必须长于 Outbox 补发的最长时限（12 小时），否则补发的重复消息会被再累加一次。
     */
    private static final Duration DEDUP_TTL = Duration.ofDays(1);

    private static final Long MISS = -1L;

    /**
     * KEYS：计数 Hash、待落库集合、去重 key（可选）；ARGV：字段、增量、对象 ID、去重 key 过期秒数。
     * 返回 -1 表示 Hash 或该字段不存在（MISS），0 表示重复消息，1 表示已累加。
     * 去重 key 在累加成功后才写入：脚本中途出错时不会留下标记，重试不会被误判为重复。
     */
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then
                return -1
            end
            if KEYS[3] and redis.call('EXISTS', KEYS[3]) == 1 then
                return 0
            end
            if redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2]) < 0 then
                redis.call('HSET', KEYS[1], ARGV[1], 0)
            end
            redis.call('SADD', KEYS[2], ARGV[3])
            if KEYS[3] then
                redis.call('SET', KEYS[3], 1, 'EX', ARGV[4])
            end
            return 1
            """, Long.class);

    /**
     * KEYS：计数 Hash；ARGV：依次为各字段名和值。只写入 Hash 中还不存在的字段，已有的值不变。
     */
    private static final byte[] BACKFILL = """
            for i = 1, #ARGV, 2 do
                redis.call('HSETNX', KEYS[1], ARGV[i], ARGV[i + 1])
            end
            return 1
            """.getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS：计数 Hash、待落库集合；ARGV：字段、新值、对象 ID。只在 Hash 存在时写入并标记为待落库，
     * 不存在时留给下次访问从 MySQL 回填。
     */
    private static final RedisScript<Long> RESET_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final CounterTables counterTables;

    private static String hashKey(CounterTarget target, long id) {
        return "counter:" + CounterTables.lower(target.name()) + ":" + id;
    }

    private static String dirtyKey(CounterTarget target) {
        return "counter:dirty:" + CounterTables.lower(target.name());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 原子地增减一项计数（结果最小为 0），并把对象标记为待落库。
     *
     * @param messageId 不为 null 时按它去重，同一个 messageId 在 24 小时内只生效一次
     * @throws IllegalStateException 回填后 Hash 或该字段仍然不存在
     */
    void increment(CounterMetric metric, long targetId, long delta, String messageId) {
        CounterTarget target = metric.target();
        List<String> keys = new ArrayList<>(List.of(hashKey(target, targetId), dirtyKey(target)));
        if (messageId != null) {
            keys.add("counter:dedup:" + messageId);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            Long result = redis.execute(INCREMENT, keys, CounterTables.field(metric), String.valueOf(delta),
                    String.valueOf(targetId), String.valueOf(DEDUP_TTL.toSeconds()));
            if (!MISS.equals(result)) {
                return;
            }
            backfill(target, List.of(targetId));
        }
        throw new IllegalStateException("计数回填后仍不存在: " + hashKey(target, targetId) + " " + CounterTables.field(metric));
    }

    /**
     * 批量读取。Redis 里没有 Hash 或缺少字段的对象，先从 MySQL 批量回填缺失的部分再读一次。
     *
     * @return 每个传入的 ID 都有一项
     */
    Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(targetIds));
        Map<Long, Counts> result = read(target, ids);
        List<Long> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<Long, Counts> loaded = backfill(target, missing);
            Map<Long, Counts> filled = read(target, missing);
            // 回填后又被删除的对象，按 MySQL 的值返回
            missing.forEach(id -> result.put(id, filled.getOrDefault(id, loaded.get(id))));
        }
        return result;
    }

    /**
     * 只读 Redis，不回填。
     *
     * @return 只包含 Hash 存在且字段齐全的对象
     */
    Map<Long, Counts> getIfPresent(CounterTarget target, Collection<Long> targetIds) {
        return read(target, List.copyOf(new LinkedHashSet<>(targetIds)));
    }

    /**
     * Hash 存在时把一项计数改为给定值，并标记为待落库：即使此前已读出旧值的落库批次随后覆盖了 MySQL，
     * 下一次落库也会写回新值。Hash 不存在时不写入。
     */
    void resetIfPresent(CounterMetric metric, long targetId, long value) {
        CounterTarget target = metric.target();
        redis.execute(RESET_IF_PRESENT, List.of(hashKey(target, targetId), dirtyKey(target)),
                CounterTables.field(metric), String.valueOf(value), String.valueOf(targetId));
    }

    /**
     * 把待落库的对象按绝对值写回 MySQL，重复写结果不变。{@code SPOP} 是原子的，多个实例同时落库不会处理同一个对象。
     * 写库失败时把这批 ID 放回待落库集合。已知缺陷，均由下次变更或对账修正：
     * <ul>
     *     <li>实例在取出之后、写库之前崩溃，这批对象的标记丢失。</li>
     *     <li>多个实例时，一个批次读出旧值后，同一对象再次变更并被另一实例先写入新值，前者随后用旧值覆盖。</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    void flush() {
        for (CounterTarget target : CounterTarget.values()) {
            List<String> ids;
            do {
                ids = redis.opsForSet().pop(dirtyKey(target), FLUSH_BATCH);
            } while (ids != null && flush(target, ids) && ids.size() == FLUSH_BATCH);
        }
    }

    /**
     * @return 是否写入成功
     */
    private boolean flush(CounterTarget target, List<String> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        try {
            // Hash 或字段缺失时（Redis 丢数据、新增了指标）先从 MySQL 回填，再整行写回
            counterTables.upsert(target, get(target, ids.stream().map(Long::valueOf).toList()));
            return true;
        } catch (RuntimeException e) {
            redis.opsForSet().add(dirtyKey(target), ids.toArray(String[]::new));
            log.error("Failed to flush counters, ids are returned to the dirty set: target={}, size={}",
                    target, ids.size(), e);
            return false;
        }
    }

    /**
     * 用 pipeline 批量 {@code HMGET}。
     *
     * @return 只包含 Hash 存在且字段齐全的对象
     */
    private Map<Long, Counts> read(CounterTarget target, List<Long> ids) {
        List<CounterMetric> metrics = CounterTables.metrics(target);
        byte[][] fields = metrics.stream().map(metric -> bytes(CounterTables.field(metric))).toArray(byte[][]::new);
        List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
            ids.forEach(id -> connection.hashCommands().hMGet(bytes(hashKey(target, id)), fields));
            return null;
        });
        Map<Long, Counts> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            List<?> values = (List<?>) replies.get(i);
            if (values.contains(null)) {
                continue;
            }
            Map<CounterMetric, Long> counts = new EnumMap<>(CounterMetric.class);
            for (int j = 0; j < metrics.size(); j++) {
                counts.put(metrics.get(j), Long.valueOf((String) values.get(j)));
            }
            result.put(ids.get(i), new Counts(counts));
        }
        return result;
    }

    /**
     * 从 MySQL 批量读出计数（没有计数行的对象按全 0），用 pipeline 逐个回填到 Redis，只写入缺失的字段。
     *
     * @return 从 MySQL 读出的计数，每个传入的 ID 都有一项
     */
    private Map<Long, Counts> backfill(CounterTarget target, List<Long> ids) {
        Map<Long, Counts> loaded = counterTables.select(target, ids);
        ids.forEach(id -> loaded.putIfAbsent(id, new Counts(Map.of())));
        List<CounterMetric> metrics = CounterTables.metrics(target);
        redis.executePipelined((RedisCallback<Object>) connection -> {
            loaded.forEach((id, counts) -> {
                List<byte[]> keysAndArgs = new ArrayList<>();
                keysAndArgs.add(bytes(hashKey(target, id)));
                metrics.forEach(metric -> {
                    keysAndArgs.add(bytes(CounterTables.field(metric)));
                    keysAndArgs.add(bytes(String.valueOf(counts.get(metric))));
                });
                connection.scriptingCommands().eval(BACKFILL, ReturnType.INTEGER, 1,
                        keysAndArgs.toArray(byte[][]::new));
            });
            return null;
        });
        return loaded;
    }
}
