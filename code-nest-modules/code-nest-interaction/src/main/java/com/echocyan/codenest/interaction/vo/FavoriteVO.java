package com.echocyan.codenest.interaction.vo;

import com.echocyan.codenest.article.api.ArticleItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.LocalDateTime;

/**
 * 我的收藏列表中的一项：文章列表项的全部字段，外加收藏时间。
 */
public record FavoriteVO(@JsonUnwrapped ArticleItem article, LocalDateTime favoritedAt) {
}
