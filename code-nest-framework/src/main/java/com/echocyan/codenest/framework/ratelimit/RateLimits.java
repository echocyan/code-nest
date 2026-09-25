package com.echocyan.codenest.framework.ratelimit;

import java.lang.annotation.*;

/**
 * {@link RateLimit} 的容器注解，由编译器在重复标注时生成，不直接使用。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {

    RateLimit[] value();
}
