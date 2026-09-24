package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("article_content")
public class ArticleContent extends AuditableEntity {

    @TableId(type = IdType.INPUT)
    private Long articleId;

    private String content;
}
