package com.echocyan.codenest.social.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * followerId 关注了 authorId。取关后再关注会再次发出。
 */
@DomainEvent("follow.created")
public record FollowCreatedEvent(long followerId, long authorId) {
}
