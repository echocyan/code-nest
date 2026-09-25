package com.echocyan.codenest.framework.cache;

/**
 * {@code cache.mode} 的取值，决定 {@link TwoLevelCache} 用到哪几级缓存。
 */
public enum CacheMode {

    /** 不缓存，每次都执行加载函数（基线）。 */
    NONE,

    /** 只用 Redis 一级缓存。 */
    REDIS
}
