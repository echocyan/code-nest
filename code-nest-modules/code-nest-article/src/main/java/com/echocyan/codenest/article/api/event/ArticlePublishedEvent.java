package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 作者发布了草稿。重复发布不会再次发出。
 */
@DomainEvent("article.published")
public record ArticlePublishedEvent(long articleId, long authorId) {
}
