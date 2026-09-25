package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.entity.ArticleTag;
import com.echocyan.codenest.article.mapper.ArticleTagMapper;
import com.echocyan.codenest.article.service.ArticleTagService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleTagServiceImpl extends ServiceImpl<ArticleTagMapper, ArticleTag> implements ArticleTagService {

    @Override
    public List<Long> listTagIds(long articleId) {
        return lambdaQuery().eq(ArticleTag::getArticleId, articleId).list().stream()
                .map(ArticleTag::getTagId)
                .toList();
    }

    @Override
    public Map<Long, List<Long>> listTagIds(Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        return lambdaQuery().in(ArticleTag::getArticleId, articleIds).orderByAsc(ArticleTag::getTagId).list().stream()
                .collect(Collectors.groupingBy(ArticleTag::getArticleId,
                        Collectors.mapping(ArticleTag::getTagId, Collectors.toList())));
    }

    @Override
    @Transactional
    public void replaceTags(long articleId, List<Long> tagIds) {
        lambdaUpdate().eq(ArticleTag::getArticleId, articleId).remove();
        if (tagIds.isEmpty()) {
            return;
        }
        saveBatch(tagIds.stream().map(tagId -> {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticleId(articleId);
            articleTag.setTagId(tagId);
            return articleTag;
        }).toList());
    }
}
