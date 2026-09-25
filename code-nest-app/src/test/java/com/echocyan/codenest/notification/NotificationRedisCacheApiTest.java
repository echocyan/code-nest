package com.echocyan.codenest.notification;

import com.echocyan.codenest.support.RedisCache;

/**
 * 在 redis 缓存档下运行 {@link NotificationApiTest} 的全部测试。
 */
@RedisCache
class NotificationRedisCacheApiTest extends NotificationApiTest {
}
