package com.echocyan.codenest.article.service;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ArticleApiImpl implements ArticleApi {

    private final ArticleMapper articleMapper;
    private final ArticleConverter articleConverter;

    @Override
    public Optional<ArticleState> findState(long articleId) {
        return Optional.ofNullable(articleMapper.selectById(articleId)).map(articleConverter::toState);
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return articleMapper.selectByIds(articleIds).stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity()));
    }
}
