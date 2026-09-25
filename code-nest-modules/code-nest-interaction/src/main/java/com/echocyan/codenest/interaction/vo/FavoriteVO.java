package com.echocyan.codenest.interaction.vo;

import com.echocyan.codenest.article.api.ArticleBrief;

import java.time.LocalDateTime;

/**
 * 我的收藏列表中的一项。
 */
public record FavoriteVO(ArticleBrief article, LocalDateTime favoritedAt) {
}
