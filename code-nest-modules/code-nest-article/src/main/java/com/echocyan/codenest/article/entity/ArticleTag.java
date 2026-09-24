package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章与标签的关联，主键为 (articleId, tagId)。
 */
@Getter
@Setter
@TableName("article_tag")
public class ArticleTag extends AuditableEntity {

    private Long articleId;

    private Long tagId;
}
