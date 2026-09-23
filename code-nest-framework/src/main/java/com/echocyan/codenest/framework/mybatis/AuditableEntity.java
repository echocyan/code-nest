package com.echocyan.codenest.framework.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 所有表共有的创建与更新时间，由 {@link AuditMetaObjectHandler} 自动填充。
 */
@Getter
@Setter
public abstract class AuditableEntity {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
