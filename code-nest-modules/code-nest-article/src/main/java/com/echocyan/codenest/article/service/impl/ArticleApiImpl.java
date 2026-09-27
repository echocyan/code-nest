package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.*;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CommentConverter;
import com.echocyan.codenest.article.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class ArticleApiImpl implements ArticleApi {

    private final ArticleService articleService;
    private final ArticleConverter articleConverter;
    private final CommentService commentService;
    private final CommentConverter commentConverter;

    @Override
    public Optional<ArticleState> findState(long articleId) {
        return Optional.ofNullable(articleService.getById(articleId)).map(articleConverter::toState);
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds) {
        return articleService.getBriefs(articleIds);
    }

    @Override
    public List<ArticleItem> listPublishedItems(List<Long> articleIds) {
        return articleService.listPublishedItems(articleIds);
    }

    @Override
    public Optional<ArticleSnapshot> findSnapshot(long articleId) {
        return Optional.ofNullable(articleService.getIncludingDeleted(articleId))
                .map(article -> articleService.toSnapshots(List.of(article)).getFirst());
    }

    @Override
    public List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit) {
        return articleService.toSnapshots(articleService.listPublishedAfter(afterId, limit));
    }

    @Override
    public List<ArticleState> listPublishedStates(Long afterId, int limit) {
        return articleService.listPublishedAfter(afterId, limit).stream().map(articleConverter::toState).toList();
    }

    @Override
    public List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit) {
        return articleService.toSnapshots(articleService.listUpdatedSinceIncludingDeleted(since, afterId, limit));
    }

    @Override
    public Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentService.listByIds(commentIds).stream()
                .map(commentConverter::toBrief)
                .collect(Collectors.toMap(CommentBrief::id, Function.identity()));
    }
}
