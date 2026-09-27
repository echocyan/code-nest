package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.*;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.user.api.UserBrief;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface ArticleConverter {

    ArticleState toState(Article article);

    ArticleBrief toBrief(Article article);

    @Mapping(target = "deleted", expression = "java(article.getDeleted() == 1)")
    ArticleSnapshot toSnapshot(Article article, String content, List<Long> tagIds, List<String> tagNames);

    /**
     * 分类、作者都有 id 属性，需指明取文章的。
     */
    @Mapping(target = "id", source = "brief.id")
    ArticleItem toItem(ArticleBrief brief, CategoryBrief category, UserBrief author, ArticleCounts counts);

    @Mapping(target = "id", source = "article.id")
    @Mapping(target = "content", source = "content")
    ArticleDetailVO toDetailVO(Article article, String content, CategoryBrief category, List<TagVO> tags,
                               UserBrief author, ArticleCounts counts);
}
