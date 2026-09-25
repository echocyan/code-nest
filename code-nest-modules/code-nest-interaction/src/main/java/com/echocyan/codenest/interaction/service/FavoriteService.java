package com.echocyan.codenest.interaction.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.interaction.InteractionErrorCode;
import com.echocyan.codenest.interaction.entity.Favorite;
import com.echocyan.codenest.interaction.vo.FavoriteVO;

import java.util.Collection;
import java.util.Set;

/**
 * 收藏与取消收藏。两者都幂等，只有真的插入或删除了一行才更新文章收藏数。
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * @throws BizException {@link InteractionErrorCode#ARTICLE_NOT_FOUND} 文章不存在、已删除或是草稿
     */
    void favorite(long userId, long articleId);

    /**
     * @throws BizException {@link InteractionErrorCode#ARTICLE_NOT_FOUND} 文章不存在、已删除或是草稿
     */
    void unfavorite(long userId, long articleId);

    /**
     * 按收藏时间倒序翻阅自己的收藏，以收藏记录的 ID 为游标。已删除的文章会被滤掉，因此一页可能不足 size 条。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     */
    CursorResult<FavoriteVO> listMine(long userId, Long cursor, int size);

    /**
     * 给定文章中用户收藏过的那些，走 (user_id, article_id) 唯一索引。
     */
    Set<Long> listFavoritedArticleIds(long userId, Collection<Long> articleIds);
}
