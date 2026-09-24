package com.echocyan.codenest.interaction.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.interaction.InteractionErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 点赞、收藏的前置检查：只能对已发布的文章操作。
 */
@Component
@RequiredArgsConstructor
class PublishedArticles {

    private final ArticleApi articleApi;

    /**
     * @throws BizException {@link InteractionErrorCode#ARTICLE_NOT_FOUND} 文章不存在、已删除或是草稿
     */
    ArticleState require(long articleId) {
        return articleApi.findState(articleId)
                .filter(article -> article.status() == ArticleStatus.PUBLISHED)
                .orElseThrow(() -> new BizException(InteractionErrorCode.ARTICLE_NOT_FOUND));
    }
}
