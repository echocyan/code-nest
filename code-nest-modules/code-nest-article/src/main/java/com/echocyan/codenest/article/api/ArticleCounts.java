package com.echocyan.codenest.article.api;

/**
 * 文章的点赞、收藏、评论、浏览数。
 */
public record ArticleCounts(long likeCount, long favoriteCount, long commentCount, long viewCount) {
}
