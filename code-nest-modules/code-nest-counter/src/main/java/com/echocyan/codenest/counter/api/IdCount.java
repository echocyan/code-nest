package com.echocyan.codenest.counter.api;

/**
 * 从关系表或内容表重新统计出的一个对象的精确计数。
 */
public record IdCount(long id, long count) {
}
