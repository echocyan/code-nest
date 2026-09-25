package com.echocyan.codenest.search.dto;

/**
 * 搜索结果的排序方式。
 */
public enum SearchSort {

    /**
     * 按相关度，默认。
     */
    RELEVANCE,

    /**
     * 按发布时间倒序。
     */
    LATEST
}
