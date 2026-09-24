package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.entity.ArticleTag;
import com.echocyan.codenest.article.mapper.ArticleTagMapper;
import com.echocyan.codenest.article.service.ArticleTagService;
import java.util.List;
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
