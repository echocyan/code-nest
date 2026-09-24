package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.common.util.Texts;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(imports = Texts.class)
public interface CommentConverter {

    @Mapping(target = "summary", expression = "java(Texts.head(comment.getContent(), CommentBrief.SUMMARY_LENGTH))")
    CommentBrief toBrief(Comment comment);
}
