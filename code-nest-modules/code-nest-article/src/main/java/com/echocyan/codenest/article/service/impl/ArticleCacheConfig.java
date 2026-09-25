package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.framework.cache.BloomFilter;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文章详情与文章摘要的缓存，文章编辑、发布、删除后都要失效，见 {@link ArticleServiceImpl#evictCache}。
 * 文章详情是两级缓存，用布隆过滤器 {@code bf:article} 拦截不存在的文章 ID：文章创建时加入，
 * 启动时如果不存在就重建（{@link ArticleBloomFilterLoader}）。
 */
@Configuration(proxyBeanMethods = false)
class ArticleCacheConfig {

    @Bean
    BloomFilter articleBloomFilter(TwoLevelCaches caches) {
        return caches.bloomFilter("article");
    }

    @Bean
    TwoLevelCache<CachedArticleDetail> articleDetailCache(TwoLevelCaches caches, BloomFilter articleBloomFilter) {
        return caches.createTwoLevel("article:detail", CachedArticleDetail.class, articleBloomFilter);
    }

    @Bean
    TwoLevelCache<ArticleBrief> articleBriefCache(TwoLevelCaches caches) {
        return caches.create("article:brief", ArticleBrief.class);
    }
}
