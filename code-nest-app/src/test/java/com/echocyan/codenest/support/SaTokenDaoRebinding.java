package com.echocyan.codenest.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.SmartLifecycle;

/**
 * Sa-Token 把存储层放在静态的 {@link SaManager} 里，只指向最后创建的 Spring 上下文。测试在不同模式开关的上下文之间切换时，
 * Spring 会暂停不活跃的上下文、停掉它的 Redis 连接；这里在上下文启动或恢复时，把 {@link SaManager} 指回本上下文的存储层。
 */
@TestComponent
class SaTokenDaoRebinding implements SmartLifecycle {

    private final SaTokenDao saTokenDao;

    private volatile boolean running;

    SaTokenDaoRebinding(SaTokenDao saTokenDao) {
        this.saTokenDao = saTokenDao;
    }

    @Override
    public void start() {
        SaManager.setSaTokenDao(saTokenDao);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
