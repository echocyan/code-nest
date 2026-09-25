package com.echocyan.codenest.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

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
