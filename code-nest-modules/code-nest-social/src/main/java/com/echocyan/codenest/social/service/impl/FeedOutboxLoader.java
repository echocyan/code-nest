package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.social.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 启动时如果发件箱的重建完成标记不存在，按全部已发布文章重建发件箱。与 {@code feed.mode} 无关，两档都执行。
 */
@Component
@RequiredArgsConstructor
class FeedOutboxLoader implements SmartInitializingSingleton {

    private final FeedFanoutService feedFanoutService;

    @Override
    public void afterSingletonsInstantiated() {
        feedFanoutService.rebuildOutboxesIfAbsent();
    }
}
