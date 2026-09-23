package com.echocyan.codenest.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * 把 {@link SharedContainers} 的连接信息注入 Spring 上下文；集成测试与 TestCodeNestApplication 共用。
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(SharedContainers.class)
public class TestcontainersConfiguration {
}
