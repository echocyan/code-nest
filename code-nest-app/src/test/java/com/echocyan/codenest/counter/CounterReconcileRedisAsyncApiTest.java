package com.echocyan.codenest.counter;

import com.echocyan.codenest.support.RedisAsyncCounter;

/**
 * 在 redis-async 计数档下运行 {@link CounterReconcileApiTest} 的全部测试。
 */
@RedisAsyncCounter
class CounterReconcileRedisAsyncApiTest extends CounterReconcileApiTest {
}
