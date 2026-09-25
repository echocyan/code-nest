package com.echocyan.codenest.article;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link CommentApiTest} 的全部测试。
 */
@RedisCache
class CommentRedisCacheApiTest extends CommentApiTest {
}
