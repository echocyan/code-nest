package com.echocyan.codenest.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.IndexState;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.framework.lock.RedisLock;
import com.echocyan.codenest.search.dto.SearchSort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ES 中的文章索引。真实索引名为 {@code article_v{n}}，读写都经别名 {@value #ALIAS}。
 * <p>
 * 写入与删除都以 {@link ArticleSnapshot#version()} 作外部版本号：ES 只接受比已有版本更大的写入，
 * 所以旧版本的数据晚到时被拒绝（409），直接忽略。
 * <p>
 * {@link #rebuild()} 零停机重建：导入期间读写仍走旧索引，切换别名是原子的，切换后再追补导入期间的变更。
 * <p>
 * 启动时如果别名不存在，就在所有单例创建完、MQ 消费者启动前执行一次重建：全量导入完成后才挂上别名，
 * 导入中断时别名仍不存在，下次启动重来。多个实例同时启动时只有抢到重建锁的实例建索引，其余跳过；
 * 别名出现前跳过的实例搜索和同步都会失败，这期间的变更由重建最后的追补写入。
 */
@Slf4j
@Component
public class ArticleIndex implements SmartInitializingSingleton {

    public static final String ALIAS = "article";

    private static final String MAPPING = "search/article-index.json";

    /**
     * 全量导入时每批的文章数。
     */
    private static final int IMPORT_BATCH_SIZE = 500;

    private static final int VERSION_CONFLICT = 409;

    /**
     * 锁的过期时间，要长于一次重建的耗时；实例崩溃时锁最迟在这之后释放。
     */
    private static final Duration REBUILD_LOCK_TTL = Duration.ofHours(1);

    private final ElasticsearchClient client;
    private final ArticleApi articleApi;
    private final RedisLock redisLock;
    private final String alias;
    private final String indexPrefix;
    private final String rebuildLockKey;
    private final ArticleSearcher searcher;

    @Autowired
    public ArticleIndex(ElasticsearchClient client, ArticleApi articleApi, RedisLock redisLock) {
        this(client, articleApi, redisLock, ALIAS);
    }

    /**
     * @param alias 读写经过的别名；真实索引名为 {@code <alias>_v{n}}，重建锁为 {@code search:<alias>:rebuild:lock}
     */
    public ArticleIndex(ElasticsearchClient client, ArticleApi articleApi, RedisLock redisLock, String alias) {
        this.client = client;
        this.articleApi = articleApi;
        this.redisLock = redisLock;
        this.alias = alias;
        this.indexPrefix = alias + "_v";
        this.rebuildLockKey = "search:" + alias + ":rebuild:lock";
        this.searcher = new ArticleSearcher(client, alias);
    }

    /**
     * 在已发布文章中按关键词检索，按相关度或发布时间排序并高亮，见 {@link ArticleSearcher}。调用方已校验翻页深度。
     *
     * @param categoryId 为 null 时不按分类筛选
     * @param tagId      为 null 时不按标签筛选
     */
    public PageResult<Match> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page,
                                    long size) {
        return searcher.search(keyword, categoryId, tagId, sort, page, size);
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (aliasExists()) {
            return;
        }
        // 拿到锁后再判断一次：别的实例可能刚建好并释放了锁
        boolean ran = redisLock.tryRun(rebuildLockKey, REBUILD_LOCK_TTL, () -> {
            if (!aliasExists()) {
                rebuildLocked();
            }
        });
        if (!ran) {
            log.info("Another instance is building search index for alias {}, skipped", alias);
        }
    }

    /**
     * 重建索引：
     * <ol>
     *     <li>新建下一版本的索引，按文章 ID 游标分批全量导入，刷新后使其可搜；</li>
     *     <li>在一个请求里把别名从旧索引移到新索引；</li>
     *     <li>把 {@code updated_at} 不早于重建开始时间的文章按最新状态写入新索引。导入期间的变更由消费者写进了旧索引，
     *     新索引里可能是旧版本；外部版本号保证重复写入无害；</li>
     *     <li>删除其余的 {@code article_v{n}}：旧索引，以及此前中断的重建留下、没挂别名的索引。</li>
     * </ol>
     * 别名不存在时同样适用，只是没有旧索引可切走。
     * 多实例之间用 Redis 锁互斥，同一时刻只有一个重建在运行。
     *
     * @return 新索引名与导入、追补的文章数；已有重建在运行时为空
     */
    public Optional<RebuildResult> rebuild() {
        Optional<RebuildResult> result = redisLock.tryRun(rebuildLockKey, REBUILD_LOCK_TTL, this::rebuildLocked);
        if (result.isEmpty()) {
            log.info("Search index rebuild is already running, skipped");
        }
        return result;
    }

    /**
     * 持有重建锁时执行 {@link #rebuild()} 的各步。
     */
    private RebuildResult rebuildLocked() {
        try {
            // updated_at 是秒级 DATETIME，写入时四舍五入；向下取整到秒，不漏掉开始那一秒内的变更
            LocalDateTime startedAt = DateTimes.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, IndexState> existing = client.indices().get(get -> get.index(indexPrefix + "*")).indices();
            Set<String> oldIndices = existing.entrySet().stream()
                    .filter(entry -> entry.getValue().aliases().containsKey(alias))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            String index = indexPrefix + nextVersion(existing.keySet());
            create(index);
            long imported = importAll(index);
            client.indices().refresh(refresh -> refresh.index(index));
            client.indices().updateAliases(update -> {
                oldIndices.forEach(old -> update.actions(action -> action
                        .remove(remove -> remove.index(old).alias(alias))));
                return update.actions(action -> action.add(add -> add.index(index).alias(alias)));
            });
            long caughtUp = catchUp(startedAt);
            if (!existing.isEmpty()) {
                client.indices().delete(delete -> delete.index(List.copyOf(existing.keySet())));
            }
            log.info("Rebuilt search index {}: imported {}, caught up {}, deleted {}", index, imported, caughtUp,
                    existing.keySet());
            return new RebuildResult(index, imported, caughtUp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按文章的最新状态同步：已发布就写入，已删除或是草稿就删除。
     */
    public void sync(long articleId) {
        articleApi.findSnapshot(articleId).ifPresent(this::sync);
    }

    private void sync(ArticleSnapshot snapshot) {
        if (snapshot.searchable()) {
            save(snapshot);
        } else {
            remove(snapshot.id(), snapshot.version());
        }
    }

    /**
     * 写入一篇文章；索引里已有相同或更新的版本时什么都不做。
     */
    private void save(ArticleSnapshot snapshot) {
        ignoringVersionConflict(() -> client.index(index -> index
                .index(alias)
                .requireAlias(true)
                .id(String.valueOf(snapshot.id()))
                .version((long) snapshot.version())
                .versionType(VersionType.External)
                .document(ArticleDocument.of(snapshot))));
    }

    /**
     * 删除一篇文章；version 是删除后的版本号，索引里已有相同或更新的版本时什么都不做。
     */
    private void remove(long articleId, long version) {
        ignoringVersionConflict(() -> client.delete(delete -> delete
                .index(alias)
                .id(String.valueOf(articleId))
                .version(version)
                .versionType(VersionType.External)));
    }

    private boolean aliasExists() {
        try {
            return client.indices().existsAlias(exists -> exists.name(alias)).value();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按 mapping 新建索引，不挂别名。
     */
    private void create(String index) throws IOException {
        try (InputStream mapping = new ClassPathResource(MAPPING).getInputStream()) {
            client.indices().create(create -> create.withJson(mapping).index(index));
        }
        log.info("Created search index {}", index);
    }

    /**
     * 已有 {@code article_v{n}} 中最大的 n 加 1，没有时为 1。
     */
    private int nextVersion(Set<String> indices) {
        return indices.stream()
                .mapToInt(name -> Integer.parseInt(name.substring(indexPrefix.length())))
                .max()
                .orElse(0) + 1;
    }

    /**
     * 按文章 ID 游标分批读取全部已发布文章，用 bulk 写入给定的索引。
     *
     * @return 导入的文章数
     */
    private long importAll(String index) {
        long total = forEachBatch(articleApi::listPublishedSnapshots, batch -> saveAll(index, batch));
        log.info("Imported {} published articles into search index {}", total, index);
        return total;
    }

    /**
     * 把 {@code updated_at} 不早于 since 的文章按最新状态逐篇写入或删除，经别名写入。
     *
     * @return 处理的文章数
     */
    private long catchUp(LocalDateTime since) {
        return forEachBatch((afterId, limit) -> articleApi.listSnapshotsUpdatedSince(since, afterId, limit),
                batch -> batch.forEach(this::sync));
    }

    /**
     * 以文章 ID 作游标，每批 {@value #IMPORT_BATCH_SIZE} 篇，读完为止。
     *
     * @param reader 按 (afterId, limit) 读取一批，afterId 为 null 表示从头开始
     * @return 读到的文章总数
     */
    private long forEachBatch(BiFunction<Long, Integer, List<ArticleSnapshot>> reader,
                              Consumer<List<ArticleSnapshot>> handler) {
        long total = 0;
        Long cursor = null;
        List<ArticleSnapshot> batch;
        do {
            batch = reader.apply(cursor, IMPORT_BATCH_SIZE);
            if (!batch.isEmpty()) {
                handler.accept(batch);
                total += batch.size();
                cursor = batch.getLast().id();
            }
        } while (batch.size() == IMPORT_BATCH_SIZE);
        return total;
    }

    /**
     * 批量写入；个别文章版本冲突时忽略，其他失败直接抛出。
     */
    private void saveAll(String indexName, List<ArticleSnapshot> snapshots) {
        BulkResponse response;
        try {
            response = client.bulk(bulk -> {
                for (ArticleSnapshot snapshot : snapshots) {
                    bulk.operations(operation -> operation.index(index -> index
                            .index(indexName)
                            .id(String.valueOf(snapshot.id()))
                            .version((long) snapshot.version())
                            .versionType(VersionType.External)
                            .document(ArticleDocument.of(snapshot))));
                }
                return bulk;
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null && item.status() != VERSION_CONFLICT) {
                throw new IllegalStateException("Failed to index article " + item.id() + ": " + item.error().reason());
            }
        }
    }

    private void ignoringVersionConflict(EsCall call) {
        try {
            call.run();
        } catch (ElasticsearchException e) {
            if (e.status() != VERSION_CONFLICT) {
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private interface EsCall {
        void run() throws IOException;
    }

    /**
     * 一条命中结果。
     *
     * @param titleHighlight   高亮后的标题，未命中时为 null
     * @param contentHighlight 正文的高亮片段，未命中时为 null
     */
    public record Match(long articleId, String titleHighlight, String contentHighlight) {
    }

    /**
     * 一次重建的结果。
     *
     * @param index    新索引名
     * @param imported 全量导入的已发布文章数
     * @param caughtUp 重建期间有变更、导入后又按最新状态写入或删除的文章数
     */
    public record RebuildResult(String index, long imported, long caughtUp) {
    }

    /**
     * 索引文档的字段名，与 {@link ArticleDocument} 的组件名、{@code search/article-index.json} 的 mapping 一致。
     */
    public static final class Fields {

        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String SUMMARY = "summary";
        public static final String CONTENT = "content";
        public static final String TAGS = "tags";
        public static final String TAG_IDS = "tagIds";
        public static final String CATEGORY_ID = "categoryId";
        public static final String AUTHOR_ID = "authorId";
        public static final String PUBLISHED_AT = "publishedAt";

        private Fields() {
        }
    }

    /**
     * 索引中的文档，组件名即字段名，见 {@link Fields}。作者昵称可以修改、计数变化太频繁，都不放进来。
     *
     * @param content Markdown 原文，不做清洗
     * @param tags    标签名，用于关键词与标签名完全一致时加分
     * @param tagIds  标签 ID，用于按标签筛选
     */
    record ArticleDocument(Long id, String title, String summary, String content, List<String> tags,
                           List<Long> tagIds, Long categoryId, Long authorId, LocalDateTime publishedAt) {

        static ArticleDocument of(ArticleSnapshot snapshot) {
            return new ArticleDocument(snapshot.id(), snapshot.title(), snapshot.summary(), snapshot.content(),
                    snapshot.tagNames(), snapshot.tagIds(), snapshot.categoryId(), snapshot.authorId(),
                    snapshot.publishedAt());
        }
    }
}
