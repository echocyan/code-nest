package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.*;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CommentConverter;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.service.*;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
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
    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final TagService tagService;
    private final ArticleConverter articleConverter;
    private final CommentService commentService;
    private final CommentConverter commentConverter;
    private final TwoLevelCache<ArticleBrief> briefCache;

    @Override
    public Optional<ArticleState> findState(long articleId) {
        return Optional.ofNullable(articleService.getById(articleId)).map(articleConverter::toState);
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds) {
        return briefCache.getAll(articleIds, ids -> articleService.listByIds(ids).stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity())));
    }

    @Override
    public List<ArticleBrief> listByAuthors(Collection<Long> authorIds, Long cursor, int limit) {
        return articleService.listPublishedByAuthors(authorIds, cursor, limit).stream()
                .map(articleConverter::toBrief)
                .toList();
    }

    @Override
    public PageResult<ArticleBrief> searchPublished(String keyword, Long categoryId, Long tagId, long page,
                                                    long size) {
        return articleService.searchPublished(keyword, categoryId, tagId, page, size).map(articleConverter::toBrief);
    }

    @Override
    public Optional<ArticleSnapshot> findSnapshot(long articleId) {
        return Optional.ofNullable(articleService.getIncludingDeleted(articleId))
                .map(article -> toSnapshots(List.of(article)).getFirst());
    }

    @Override
    public List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit) {
        return toSnapshots(articleService.listPublishedAfter(afterId, limit));
    }

    @Override
    public List<ArticleState> listPublishedStates(Long afterId, int limit) {
        return articleService.listPublishedAfter(afterId, limit).stream().map(articleConverter::toState).toList();
    }

    @Override
    public List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit) {
        return toSnapshots(articleService.listUpdatedSinceIncludingDeleted(since, afterId, limit));
    }

    @Override
    public Map<Long, LocalDateTime> getPublishedSince(LocalDateTime since) {
        return articleService.listPublishedSince(since).stream()
                .collect(Collectors.toMap(Article::getId, Article::getPublishedAt));
    }

    /**
     * 批量补全正文与标签，保持传入顺序。
     */
    private List<ArticleSnapshot> toSnapshots(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, String> contents = articleContentService.listByIds(ids).stream()
                .collect(Collectors.toMap(ArticleContent::getArticleId, ArticleContent::getContent));
        Map<Long, List<Long>> tagIds = articleTagService.listTagIds(ids);
        Map<Long, String> tagNames = tagService.listInOrder(
                        tagIds.values().stream().flatMap(List::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return articles.stream()
                .map(article -> {
                    List<Long> ownTagIds = tagIds.getOrDefault(article.getId(), List.of());
                    return articleConverter.toSnapshot(article, contents.get(article.getId()), ownTagIds,
                            ownTagIds.stream().map(tagNames::get).toList());
                })
                .toList();
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
