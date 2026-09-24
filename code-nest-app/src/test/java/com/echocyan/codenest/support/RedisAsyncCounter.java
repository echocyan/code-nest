package com.echocyan.codenest.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

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
