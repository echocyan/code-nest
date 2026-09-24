package com.echocyan.codenest.interaction.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.interaction.entity.Favorite;
import com.echocyan.codenest.interaction.mapper.FavoriteMapper;
import com.echocyan.codenest.interaction.service.FavoriteService;
import com.echocyan.codenest.interaction.vo.FavoriteVO;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    private final PublishedArticles publishedArticles;
    private final ArticleApi articleApi;
    private final CounterApi counterApi;

    @Override
    @Transactional
    public void favorite(long userId, long articleId) {
        publishedArticles.get(articleId);
        Favorite favorite = new Favorite();
        favorite.setId(IdWorker.getId());
        favorite.setUserId(userId);
        favorite.setArticleId(articleId);
        LocalDateTime now = DateTimes.now();
        favorite.setCreatedAt(now);
        favorite.setUpdatedAt(now);
        if (baseMapper.insertIgnore(favorite) > 0) {
            counterApi.increment(CounterMetric.ARTICLE_FAVORITE, articleId, 1);
        }
    }

    @Override
    @Transactional
    public void unfavorite(long userId, long articleId) {
        publishedArticles.get(articleId);
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
