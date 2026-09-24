package com.echocyan.codenest.interaction.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import com.echocyan.codenest.interaction.mapper.ArticleLikeMapper;
import com.echocyan.codenest.interaction.service.ArticleLikeService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleLikeServiceImpl extends ServiceImpl<ArticleLikeMapper, ArticleLike> implements ArticleLikeService {

    private final PublishedArticles publishedArticles;
    private final CounterApi counterApi;

    @Override
    @Transactional
    public void like(long userId, long articleId) {
        ArticleState article = publishedArticles.get(articleId);
        ArticleLike like = new ArticleLike();
        like.setId(IdWorker.getId());
        like.setUserId(userId);
        like.setArticleId(articleId);
        LocalDateTime now = DateTimes.now();
        like.setCreatedAt(now);
        like.setUpdatedAt(now);
        if (baseMapper.insertIgnore(like) > 0) {
            countLike(article, 1);
        }
    }

    @Override
    @Transactional
    public void unlike(long userId, long articleId) {
        ArticleState article = publishedArticles.get(articleId);
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
