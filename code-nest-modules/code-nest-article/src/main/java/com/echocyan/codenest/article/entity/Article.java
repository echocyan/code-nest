package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("article")
public class Article extends AuditableEntity {

    private Long id;

    private Long authorId;

    private Long categoryId;

    private String title;

    private String summary;

    /**
     * 编辑时整体替换，null 也要写入以清空封面。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverUrl;

    private ArticleStatus status;

    private LocalDateTime publishedAt;

    /**
     * 乐观锁版本号，每次编辑、发布、删除都自动比对并 +1；也是搜索索引的外部版本号。
     */
    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
