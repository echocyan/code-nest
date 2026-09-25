package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Outbox 记录的状态。数据库存 {@link #code}。
 */
public enum OutboxStatus {

    PENDING(0),
    SENT(1),
    /**
     * 补发累计失败达到上限，等待人工处理。
     */
    FAILED(2);

    @EnumValue
    private final int code;

    OutboxStatus(int code) {
        this.code = code;
    }
}
