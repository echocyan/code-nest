package com.echocyan.codenest.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户对文章的点赞，(userId, articleId) 唯一。
 */
@Getter
@Setter
@TableName("article_like")
public class ArticleLike extends AuditableEntity {

    private Long id;

    private Long userId;

    private Long articleId;
}
