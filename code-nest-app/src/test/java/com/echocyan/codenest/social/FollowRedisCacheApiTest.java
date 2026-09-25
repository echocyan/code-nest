package com.echocyan.codenest.social;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link FollowApiTest} 的全部测试。
 */
@RedisCache
class FollowRedisCacheApiTest extends FollowApiTest {
}
