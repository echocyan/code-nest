package com.echocyan.codenest.article.api;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文章状态。数据库存 {@link #code}，JSON 输出枚举名。
 */
public enum ArticleStatus {

    DRAFT(0),
    PUBLISHED(1);

    @EnumValue
    private final int code;

    ArticleStatus(int code) {
        this.code = code;
    }
}
