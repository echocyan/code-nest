package com.echocyan.codenest.interaction.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 用户点赞了一篇文章。取消后再点赞会再次发出。
 *
 * @param userId   点赞的人
 * @param authorId 文章作者
 */
@DomainEvent("like.created")
public record LikeCreatedEvent(long articleId, long userId, long authorId) {
}
