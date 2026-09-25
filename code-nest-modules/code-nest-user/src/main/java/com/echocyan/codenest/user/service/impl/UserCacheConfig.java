package com.echocyan.codenest.user.service.impl;

import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import com.echocyan.codenest.user.api.UserBrief;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户简要信息的缓存，用户修改资料后失效。
 */
@Configuration(proxyBeanMethods = false)
class UserCacheConfig {

    @Bean
    TwoLevelCache<UserBrief> userBriefCache(TwoLevelCaches caches) {
        return caches.create("user:brief", UserBrief.class);
    }
}
