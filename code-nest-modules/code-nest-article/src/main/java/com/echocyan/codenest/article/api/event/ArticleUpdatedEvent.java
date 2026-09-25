package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 作者编辑了文章，草稿和已发布文章都会发出。
 */
@DomainEvent("article.updated")
public record ArticleUpdatedEvent(long articleId, long authorId) {
}
