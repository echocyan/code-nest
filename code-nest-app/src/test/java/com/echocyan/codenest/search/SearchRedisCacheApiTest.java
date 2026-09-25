package com.echocyan.codenest.search;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link SearchApiTest} 的全部测试。
 */
@RedisCache
class SearchRedisCacheApiTest extends SearchApiTest {
}
