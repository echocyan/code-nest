package com.echocyan.codenest.article;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link ArticleListApiTest} 的全部测试。
 */
@RedisCache
class ArticleListRedisCacheApiTest extends ArticleListApiTest {
}
