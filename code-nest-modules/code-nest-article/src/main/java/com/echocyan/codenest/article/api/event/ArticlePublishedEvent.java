package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 作者发布了草稿。重复发布、发布后再编辑都不会发出。
 */
@DomainEvent("article.published")
public record ArticlePublishedEvent(long articleId, long authorId) {
}
