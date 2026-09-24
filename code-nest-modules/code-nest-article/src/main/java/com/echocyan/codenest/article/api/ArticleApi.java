package com.echocyan.codenest.article.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * article 模块对其他模块的门面。已删除的文章、评论视为不存在。
 */
public interface ArticleApi {

    /**
     * 查询文章的状态与作者。
     *
     * @return 文章不存在时为空
     */
    Optional<ArticleState> findState(long articleId);

    /**
     * 批量查询文章摘要，草稿也会返回，由调用方按状态过滤。
     *
     * @return 以文章 ID 为 key；不存在的文章不出现在结果中
     */
    Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds);

    /**
     * 批量查询评论或回复的摘要，不检查所属文章的状态。
     *
     * @return 以评论 ID 为 key；不存在的评论不出现在结果中
     */
    Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds);
}
