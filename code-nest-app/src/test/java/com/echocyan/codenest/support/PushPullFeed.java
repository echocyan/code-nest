package com.echocyan.codenest.support;

import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.*;

/**
 * 让测试类在 {@code feed.mode=push-pull} 档下运行，大 V 阈值降为 2 个粉丝，便于造出大 V。用法同 {@link RedisAsyncCounter}。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {"feed.mode=push-pull", "feed.big-author-threshold=2"})
public @interface PushPullFeed {
}
