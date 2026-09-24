package com.echocyan.codenest.article.vo;

/**
 * 写操作后文章的最新版本号，客户端下次编辑时带上。
 */
public record ArticleVersionVO(Long id, Integer version) {
}
