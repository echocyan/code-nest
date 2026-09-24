package com.echocyan.codenest.social.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * followerId 关注了 authorId，(followerId, authorId) 唯一。
 */
@Getter
@Setter
@TableName("follow")
public class Follow extends AuditableEntity {

    private Long id;

    private Long followerId;

    private Long authorId;
}
