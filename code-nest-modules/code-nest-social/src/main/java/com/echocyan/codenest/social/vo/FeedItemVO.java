package com.echocyan.codenest.social.vo;

import com.echocyan.codenest.user.api.UserBrief;
import java.time.LocalDateTime;

/**
 * Feed 中的一篇文章：摘要、作者简要信息与计数，不含正文。
 *
 * @param author 作者简要信息；作者不存在时为 null
 */
public record FeedItemVO(
        Long id,
        String title,
        String summary,
        String coverUrl,
        LocalDateTime publishedAt,
        UserBrief author,
        ArticleCountsVO counts) {
}
