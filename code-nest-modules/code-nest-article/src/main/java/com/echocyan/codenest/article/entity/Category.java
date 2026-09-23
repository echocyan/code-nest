package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("category")
public class Category extends AuditableEntity {

    private Long id;

    private String name;

    private Integer sort;
}
