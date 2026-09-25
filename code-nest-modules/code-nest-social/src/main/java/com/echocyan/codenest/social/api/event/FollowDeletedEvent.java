package com.echocyan.codenest.social.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * followerId 取关了 authorId。
 */
@DomainEvent("follow.deleted")
public record FollowDeletedEvent(long followerId, long authorId) {
}
