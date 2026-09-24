package com.echocyan.codenest.notification.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 通知类型。数据库存 {@link #code}，JSON 输出枚举名。
 */
public enum NotificationType {

    /** 文章被点赞，关联文章。 */
    LIKE(1),
    /** 文章被评论，关联文章与评论。 */
    COMMENT(2),
    /** 自己被回复，关联文章与回复。 */
    REPLY(3),
    /** 自己被关注，不关联内容。 */
    FOLLOW(4);

    @EnumValue
    private final int code;

    NotificationType(int code) {
        this.code = code;
    }
}
