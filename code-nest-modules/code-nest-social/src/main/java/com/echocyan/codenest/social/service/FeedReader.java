package com.echocyan.codenest.social.service;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.common.result.CursorResult;
import java.util.List;

/**
 * 读取关注 Feed 的一页文章，由 {@code feed.mode} 选择实现。
 */
public interface FeedReader {

    /**
     * 关注的作者已发布的文章，按文章 ID 倒序，以文章 ID 为游标。
     *
     * @param authorIds 读者关注的全部作者，不为空
     * @param cursor    上一页的 nextCursor，第一页为 null
     */
    CursorResult<ArticleBrief> read(long userId, List<Long> authorIds, Long cursor, int size);
}
