package com.echocyan.codenest.article;

import com.echocyan.codenest.support.RedisAsyncCounter;

/**
 * 在 redis-async 计数档下运行 {@link CommentApiTest} 的全部测试。
 */
@RedisAsyncCounter
class CommentRedisAsyncApiTest extends CommentApiTest {
}
