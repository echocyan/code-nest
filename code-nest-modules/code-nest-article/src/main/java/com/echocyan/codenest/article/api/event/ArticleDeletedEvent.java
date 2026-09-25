package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 文章被删除，草稿也会发出。
 */
@DomainEvent("article.deleted")
public record ArticleDeletedEvent(long articleId, long authorId) {
}
