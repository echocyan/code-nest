package com.echocyan.codenest.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.echocyan.codenest.common.util.DateTimes;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 自动填充 {@link AuditableEntity} 的创建与更新时间。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = DateTimes.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // strictUpdateFill 在字段已有值时不覆盖，这里要求每次更新都刷新
        setFieldValByName("updatedAt", DateTimes.now(), metaObject);
    }
}
