package com.echocyan.codenest.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * 让测试类在 {@code search.mode=es} 档下运行，用法同 {@link RedisAsyncCounter}。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = "search.mode=es")
public @interface EsSearch {
}
