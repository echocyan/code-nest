package com.echocyan.codenest.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * ES 中的文章索引。真实索引名为 {@code article_v{n}}，读写都经别名 {@value #ALIAS}。
 * <p>
 * 写入与删除都以 {@link ArticleSnapshot#version()} 作外部版本号：ES 只接受比已有版本更大的写入，
 * 所以旧版本的数据晚到时被拒绝（409），直接忽略。
 * <p>
 * 启动时如果别名不存在，就新建索引并从 article 模块全量导入。这一步在所有单例创建完、MQ 消费者启动前完成，
 * 消费者不会写入不存在的别名。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "search.mode", havingValue = "es")
@RequiredArgsConstructor
public class ArticleIndex implements SmartInitializingSingleton {

    public static final String ALIAS = "article";

    private static final String INDEX_PREFIX = ALIAS + "_v";

    private static final String MAPPING = "search/article-index.json";

    /** 全量导入时每批的文章数。 */
    private static final int IMPORT_BATCH_SIZE = 500;

    private static final int VERSION_CONFLICT = 409;

    private final ElasticsearchClient client;
    private final ArticleApi articleApi;

    @Override
    public void afterSingletonsInstantiated() {
        if (createIfAbsent()) {
            importAll();
        }
    }

    /**
     * 按文章的最新状态同步：已发布就写入，已删除或是草稿就删除。
     */
    public void sync(long articleId) {
        articleApi.findSnapshot(articleId).ifPresent(snapshot -> {
            if (snapshot.searchable()) {
                save(snapshot);
            } else {
                remove(snapshot.id(), snapshot.version());
            }
        });
    }

    /**
     * 写入一篇文章；索引里已有相同或更新的版本时什么都不做。
     */
    public void save(ArticleSnapshot snapshot) {
        ignoringVersionConflict(() -> client.index(index -> index
                .index(ALIAS)
                .requireAlias(true)
                .id(String.valueOf(snapshot.id()))
                .version((long) snapshot.version())
                .versionType(VersionType.External)
                .document(ArticleDocument.of(snapshot))));
    }

    /**
     * 删除一篇文章；version 是删除后的版本号，索引里已有相同或更新的版本时什么都不做。
     */
    public void remove(long articleId, long version) {
        ignoringVersionConflict(() -> client.delete(delete -> delete
                .index(ALIAS)
                .id(String.valueOf(articleId))
                .version(version)
                .versionType(VersionType.External)));
    }

    /**
     * 别名不存在时新建下一版本的索引并挂上别名。
     *
     * @return 是否新建了索引
     */
    private boolean createIfAbsent() {
        try {
            if (client.indices().existsAlias(exists -> exists.name(ALIAS)).value()) {
                return false;
            }
            String index = INDEX_PREFIX + nextVersion();
            try (InputStream mapping = new ClassPathResource(MAPPING).getInputStream()) {
                client.indices().create(create -> create.withJson(mapping).index(index).aliases(ALIAS, alias -> alias));
            }
            log.info("Created search index {} with alias {}", index, ALIAS);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 已有 {@code article_v{n}} 中最大的 n 加 1，没有时为 1。
     */
    private int nextVersion() throws IOException {
        return client.indices().get(get -> get.index(INDEX_PREFIX + "*")).indices().keySet().stream()
                .mapToInt(name -> Integer.parseInt(name.substring(INDEX_PREFIX.length())))
                .max()
                .orElse(0) + 1;
    }

    /**
     * 按文章 ID 游标分批读取全部已发布文章，用 bulk 写入。
     */
    private void importAll() {
        long total = 0;
        Long cursor = null;
        List<ArticleSnapshot> batch;
        do {
            batch = articleApi.listPublishedSnapshots(cursor, IMPORT_BATCH_SIZE);
            if (!batch.isEmpty()) {
                saveAll(batch);
                total += batch.size();
                cursor = batch.getLast().id();
            }
        } while (batch.size() == IMPORT_BATCH_SIZE);
        log.info("Imported {} published articles into search index", total);
    }

    /**
     * 批量写入；个别文章版本冲突时忽略，其他失败直接抛出。
     */
    private void saveAll(List<ArticleSnapshot> snapshots) {
        BulkResponse response;
        try {
            response = client.bulk(bulk -> {
                for (ArticleSnapshot snapshot : snapshots) {
                    bulk.operations(operation -> operation.index(index -> index
                            .index(ALIAS)
                            .requireAlias(true)
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
     * 索引中的文档。作者昵称可以修改、计数变化太频繁，都不放进来。
     *
     * @param content  Markdown 原文，不做清洗
     * @param tags     标签名，用于关键词与标签名完全一致时加分
     * @param tagIds   标签 ID，用于按标签筛选
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
