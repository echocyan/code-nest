package com.echocyan.codenest.interaction.vo;

/**
 * 当前用户对一篇文章是否已点赞、已收藏。
 */
public record ArticleInteractionStateVO(boolean liked, boolean favorited) {
}
