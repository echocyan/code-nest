package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 用户发表了评论或回复。
 *
 * @param commentId       评论或回复本身的 ID
 * @param userId          发表的人
 * @param rootId          0 表示评论，否则是该回复所属评论的 ID
 * @param replyToUserId   被回复的人：回复时 @ 了谁就是谁，没有 @ 时是被回复的评论的作者；评论为 null
 * @param articleAuthorId 文章作者
 */
@DomainEvent("comment.created")
public record CommentCreatedEvent(long commentId, long articleId, long userId, long rootId, Long replyToUserId,
                                  long articleAuthorId) {
}
