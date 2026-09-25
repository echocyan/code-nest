package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.framework.cache.BloomFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 启动时如果文章的布隆过滤器不存在（首次部署或 Redis 数据丢失），按全部未删除文章（含草稿）的 ID 重建。
 * 各档都执行，见 {@link BloomFilter}。
 */
@Component
@RequiredArgsConstructor
class ArticleBloomFilterLoader implements SmartInitializingSingleton {

    /** 重建时每批读取的文章 ID 数。 */
    private static final int BATCH_SIZE = 1000;

    private final BloomFilter articleBloomFilter;
    private final ArticleService articleService;

    @Override
    public void afterSingletonsInstantiated() {
        articleBloomFilter.rebuildIfAbsent(afterId -> articleService.listIdsAfter(afterId, BATCH_SIZE));
    }
}
