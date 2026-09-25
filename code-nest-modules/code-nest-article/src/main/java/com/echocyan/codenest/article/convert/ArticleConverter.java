package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.ArticleCountsVO;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.user.api.UserBrief;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ArticleConverter {

    ArticleState toState(Article article);

    ArticleBrief toBrief(Article article);

    @Mapping(target = "deleted", expression = "java(article.getDeleted() == 1)")
    ArticleSnapshot toSnapshot(Article article, String content, List<Long> tagIds, List<String> tagNames);

    /** 分类、作者都有 id 属性，需指明取文章的。 */
    @Mapping(target = "id", source = "article.id")
    ArticleItemVO toItemVO(Article article, CategoryVO category, UserBrief author, ArticleCountsVO counts);

    @Mapping(target = "id", source = "article.id")
    @Mapping(target = "content", source = "content")
    ArticleDetailVO toDetailVO(Article article, String content, CategoryVO category, List<TagVO> tags,
                               UserBrief author, ArticleCountsVO counts);
}
