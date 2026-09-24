package com.echocyan.codenest.interaction;

import com.echocyan.codenest.support.RedisAsyncCounter;

/**
 * 在 redis-async 计数档下运行 {@link LikeAndFavoriteApiTest} 的全部测试。
 */
@RedisAsyncCounter
class LikeAndFavoriteRedisAsyncApiTest extends LikeAndFavoriteApiTest {
}
