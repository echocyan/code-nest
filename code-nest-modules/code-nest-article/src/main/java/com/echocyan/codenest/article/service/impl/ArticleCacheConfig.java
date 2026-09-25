package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文章详情与文章摘要的缓存，文章编辑、发布、删除后都要失效，见 {@link ArticleServiceImpl#evictCache}。
 */
@Configuration(proxyBeanMethods = false)
class ArticleCacheConfig {

    @Bean
    TwoLevelCache<CachedArticleDetail> articleDetailCache(TwoLevelCaches caches) {
        return caches.create("article:detail", CachedArticleDetail.class);
    }

    @Bean
    TwoLevelCache<ArticleBrief> articleBriefCache(TwoLevelCaches caches) {
        return caches.create("article:brief", ArticleBrief.class);
    }
}
