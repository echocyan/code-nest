package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.ArticleErrorCode;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.article.vo.CommentVO;
import com.echocyan.codenest.article.vo.ReplyVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.CursorResult;

/**
 * 评论与回复的发表、删除与列表。
 */
public interface CommentService extends IService<Comment> {

    /**
     * 对已发布的文章发表评论，文章评论数 +1。
     *
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    Comment comment(long articleId, long userId, String content);

    /**
     * 文章的评论，按时间倒序，每条带回复数。已删除的评论下面还有回复时保留位置、隐去内容，否则不返回。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     * @throws BizException {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    CursorResult<CommentVO> listComments(long articleId, Long cursor, int size);

    /**
     * 在评论下发表回复，评论回复数和文章评论数各 +1。对回复再回复时，新回复仍挂在同一条评论下。
     *
     * @param commentId     被回复的评论或回复
     * @param replyToUserId 回复 @某人；为 null 时，回复评论即回复评论本身，回复某条回复即 @ 该回复的作者
     * @throws BizException {@link ArticleErrorCode#COMMENT_NOT_FOUND}、{@link ArticleErrorCode#ARTICLE_NOT_FOUND}、
     *                      {@link ArticleErrorCode#REPLY_TO_USER_INVALID}
     */
    Comment reply(long commentId, long userId, String content, Long replyToUserId);

    /**
     * 评论下的全部回复，平铺，按时间正序。评论已删除时，仍可查看保留下来的回复。
     *
     * @param cursor 上一页的 nextCursor，第一页为 null
     * @throws BizException {@link ArticleErrorCode#COMMENT_NOT_FOUND}、{@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    CursorResult<ReplyVO> listReplies(long commentId, Long cursor, int size);

    /**
     * 软删除评论或回复。删除评论时文章评论数 -1，其下的回复保留；删除回复时评论回复数和文章评论数各 -1。
     *
     * @throws BizException {@link ArticleErrorCode#COMMENT_NOT_FOUND}、{@link CommonErrorCode#FORBIDDEN}
     */
    void delete(long id, long userId);
}
