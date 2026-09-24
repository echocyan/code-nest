package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CommentConverter;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.CommentService;
import com.echocyan.codenest.common.result.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return articleService.listByIds(articleIds).stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity()));
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
    public Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentService.listByIds(commentIds).stream()
                .map(commentConverter::toBrief)
                .collect(Collectors.toMap(CommentBrief::id, Function.identity()));
    }
}
