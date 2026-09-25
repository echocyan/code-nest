package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 作者删除了文章，草稿和已发布文章都会发出。
 */
@DomainEvent("article.deleted")
public record ArticleDeletedEvent(long articleId, long authorId) {
}
