package com.echocyan.codenest.social;

import com.echocyan.codenest.support.RedisAsyncCounter;

/**
 * 在 redis-async 计数档下运行 {@link FollowApiTest} 的全部测试。
 */
@RedisAsyncCounter
class FollowRedisAsyncApiTest extends FollowApiTest {
}
