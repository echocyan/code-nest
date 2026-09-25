package com.echocyan.codenest.social.service;

import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.vo.FeedItemVO;

/**
 * 关注 Feed：我关注的作者已发布的文章。
 */
public interface FeedService {

    /**
     * 按文章 ID 倒序翻阅，以文章 ID 为游标；没有关注任何人时为空。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     */
    CursorResult<FeedItemVO> read(long userId, Long cursor, int size);
}
