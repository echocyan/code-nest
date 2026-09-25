package com.echocyan.codenest.interaction.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import com.echocyan.codenest.interaction.mapper.ArticleLikeMapper;
import com.echocyan.codenest.interaction.service.ArticleLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleLikeServiceImpl extends ServiceImpl<ArticleLikeMapper, ArticleLike> implements ArticleLikeService,
        CounterSource {

    private final PublishedArticles publishedArticles;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public void like(long userId, long articleId) {
        ArticleState article = publishedArticles.require(articleId);
        ArticleLike like = new ArticleLike();
        like.setUserId(userId);
        like.setArticleId(articleId);
        like.setAuthorId(article.authorId());
        try {
            save(like);
        } catch (DuplicateKeyException e) {
            // 已点过赞（包括并发的重复请求），不产生变化；MySQL 只回滚这一条语句，事务可以继续
            return;
        }
        countLike(article, 1);
        eventPublisher.publish(new LikeCreatedEvent(articleId, userId, article.authorId()));
    }

    @Override
    @Transactional
    public void unlike(long userId, long articleId) {
        ArticleState article = publishedArticles.require(articleId);
        boolean removed = lambdaUpdate()
                .eq(ArticleLike::getUserId, userId)
                .eq(ArticleLike::getArticleId, articleId)
                .remove();
        if (removed) {
            countLike(article, -1);
        }
    }

    private void countLike(ArticleState article, long delta) {
        counterApi.increment(CounterMetric.ARTICLE_LIKE, article.id(), delta);
        counterApi.increment(CounterMetric.USER_LIKE_RECEIVED, article.authorId(), delta);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.ARTICLE_LIKE, CounterMetric.USER_LIKE_RECEIVED);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return switch (metric) {
            case ARTICLE_LIKE -> baseMapper.countByArticle(afterId, limit);
            case USER_LIKE_RECEIVED -> baseMapper.countByAuthor(afterId, limit);
            default -> throw new IllegalArgumentException("不负责的计数指标: " + metric);
        };
    }

    @Override
    public Set<Long> listLikedArticleIds(long userId, Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery()
                .select(ArticleLike::getArticleId)
                .eq(ArticleLike::getUserId, userId)
                .in(ArticleLike::getArticleId, articleIds)
                .list().stream()
                .map(ArticleLike::getArticleId)
                .collect(Collectors.toSet());
    }
}
