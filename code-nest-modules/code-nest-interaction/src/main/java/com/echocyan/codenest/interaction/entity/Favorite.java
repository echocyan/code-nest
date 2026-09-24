package com.echocyan.codenest.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户收藏的文章，(userId, articleId) 唯一。
 */
@Getter
@Setter
@TableName("favorite")
public class Favorite extends AuditableEntity {

    private Long id;

    private Long userId;

    private Long articleId;
}
