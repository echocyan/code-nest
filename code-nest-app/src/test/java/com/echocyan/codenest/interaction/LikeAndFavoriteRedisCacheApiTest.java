package com.echocyan.codenest.interaction;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link LikeAndFavoriteApiTest} 的全部测试。
 */
@RedisCache
class LikeAndFavoriteRedisCacheApiTest extends LikeAndFavoriteApiTest {
}
