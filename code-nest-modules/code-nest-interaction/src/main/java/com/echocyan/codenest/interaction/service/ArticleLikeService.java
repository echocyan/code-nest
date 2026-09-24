package com.echocyan.codenest.interaction.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.interaction.InteractionErrorCode;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import java.util.Collection;
import java.util.Set;

/**
 * 点赞与取消点赞。两者都幂等，只有真的插入或删除了一行才更新文章点赞数和作者获赞数。
 */
public interface ArticleLikeService extends IService<ArticleLike> {

    /**
     * @throws BizException {@link InteractionErrorCode#ARTICLE_NOT_FOUND} 文章不存在、已删除或是草稿
     */
    void like(long userId, long articleId);

    /**
     * @throws BizException {@link InteractionErrorCode#ARTICLE_NOT_FOUND} 文章不存在、已删除或是草稿
     */
    void unlike(long userId, long articleId);

    /**
     * 给定文章中用户点过赞的那些，走 (user_id, article_id) 唯一索引。
     */
    Set<Long> listLikedArticleIds(long userId, Collection<Long> articleIds);
}
