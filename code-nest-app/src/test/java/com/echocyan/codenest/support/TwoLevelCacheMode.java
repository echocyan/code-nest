package com.echocyan.codenest.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * 让测试类在 {@code cache.mode=two-level} 档下运行。用法是继承一个已有的 HTTP 测试类并标上本注解，
 * 在各档下跑同一组测试；同一档的测试类共用一个 Spring 上下文。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "cache.mode=two-level")
public @interface TwoLevelCacheMode {
}
