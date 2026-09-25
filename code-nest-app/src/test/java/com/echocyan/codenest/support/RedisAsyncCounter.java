package com.echocyan.codenest.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * 让测试类在 {@code counter.mode=redis-async} 档下运行。用法是继承一个已有的 HTTP 测试类并标上本注解，
 * 在两档下跑同一组测试；同一档的测试类共用一个 Spring 上下文。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "counter.mode=redis-async")
public @interface RedisAsyncCounter {
}
