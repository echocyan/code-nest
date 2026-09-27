package com.echocyan.codenest.framework.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 订阅本地缓存的失效广播，交给 {@link TwoLevelCaches} 处理。
 * Pub/Sub 不保证送达，漏收的实例靠本地缓存 60 秒的过期时间兜底。
 */
@Configuration(proxyBeanMethods = false)
class CacheInvalidationConfig {

    @Bean
    RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     TwoLevelCaches caches) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(caches, new ChannelTopic(TwoLevelCaches.INVALIDATE_CHANNEL));
        return container;
    }
}
