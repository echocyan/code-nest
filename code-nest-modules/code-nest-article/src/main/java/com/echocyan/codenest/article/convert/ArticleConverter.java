package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.entity.Article;
import org.mapstruct.Mapper;

@Mapper
public interface ArticleConverter {

    ArticleState toState(Article article);

    ArticleBrief toBrief(Article article);
}
