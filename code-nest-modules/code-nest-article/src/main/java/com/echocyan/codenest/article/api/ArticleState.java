package com.echocyan.codenest.article.api;

/**
 * 文章的状态与归属，供其他模块做前置检查（如只能对已发布文章点赞）。
 */
public record ArticleState(Long id, Long authorId, ArticleStatus status) {
}
