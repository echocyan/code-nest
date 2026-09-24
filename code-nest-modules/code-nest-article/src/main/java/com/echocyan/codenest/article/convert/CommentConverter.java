package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.entity.Comment;
import org.mapstruct.Mapper;

@Mapper
public interface CommentConverter {

    CommentBrief toBrief(Comment comment);
}
