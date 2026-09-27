package com.echocyan.codenest.social.service.impl;

import com.echocyan.codenest.social.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 启动时重建 Redis 里缺失的 Feed 数据：发件箱与识别大 V 用的粉丝数，见 {@link FeedFanoutService#rebuildIfAbsent}。
 */
@Component
@RequiredArgsConstructor
class FeedLoader implements SmartInitializingSingleton {

    private final FeedFanoutService feedFanoutService;

    @Override
    public void afterSingletonsInstantiated() {
        feedFanoutService.rebuildIfAbsent();
    }
}
