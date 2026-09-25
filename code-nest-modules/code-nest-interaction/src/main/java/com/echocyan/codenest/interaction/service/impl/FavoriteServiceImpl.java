package com.echocyan.codenest.interaction.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.interaction.entity.Favorite;
import com.echocyan.codenest.interaction.mapper.FavoriteMapper;
import com.echocyan.codenest.interaction.service.FavoriteService;
import com.echocyan.codenest.interaction.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService,
        CounterSource {

    private final PublishedArticles publishedArticles;
    private final ArticleApi articleApi;
    private final CounterApi counterApi;

    @Override
    @Transactional
    public void favorite(long userId, long articleId) {
        publishedArticles.require(articleId);
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setArticleId(articleId);
        try {
            save(favorite);
        } catch (DuplicateKeyException e) {
            // 已收藏过（包括并发的重复请求），不产生变化；MySQL 只回滚这一条语句，事务可以继续
            return;
        }
        counterApi.increment(CounterMetric.ARTICLE_FAVORITE, articleId, 1);
    }

    @Override
    @Transactional
    public void unfavorite(long userId, long articleId) {
        publishedArticles.require(articleId);
        boolean removed = lambdaUpdate()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getArticleId, articleId)
                .remove();
        if (removed) {
            counterApi.increment(CounterMetric.ARTICLE_FAVORITE, articleId, -1);
        }
    }

    @Override
    public CursorResult<FavoriteVO> listMine(long userId, Long cursor, int size) {
        // 多取一条判断是否还有下一页
        List<Favorite> favorites = lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .lt(cursor != null, Favorite::getId, cursor)
                .orderByDesc(Favorite::getId)
                .last("LIMIT " + (size + 1))
                .list();
        boolean hasMore = favorites.size() > size;
        List<Favorite> page = hasMore ? favorites.subList(0, size) : favorites;
        if (page.isEmpty()) {
            return CursorResult.empty();
        }
        Map<Long, ArticleBrief> briefs = articleApi.getBriefs(page.stream().map(Favorite::getArticleId).toList());
        List<FavoriteVO> list = page.stream()
                .filter(favorite -> {
                    ArticleBrief brief = briefs.get(favorite.getArticleId());
                    return brief != null && brief.status() == ArticleStatus.PUBLISHED;
                })
                .map(favorite -> new FavoriteVO(briefs.get(favorite.getArticleId()), favorite.getCreatedAt()))
                .toList();
        return new CursorResult<>(list, hasMore ? page.getLast().getId() : null, hasMore);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.ARTICLE_FAVORITE);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return baseMapper.countByArticle(afterId, limit);
    }

    @Override
    public Set<Long> listFavoritedArticleIds(long userId, Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery()
                .select(Favorite::getArticleId)
                .eq(Favorite::getUserId, userId)
                .in(Favorite::getArticleId, articleIds)
                .list().stream()
                .map(Favorite::getArticleId)
                .collect(Collectors.toSet());
    }
}
