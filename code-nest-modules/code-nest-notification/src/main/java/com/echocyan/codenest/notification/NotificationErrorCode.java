package com.echocyan.codenest.notification;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * notification 模块错误码，号段 5xxxx。
 */
public enum NotificationErrorCode implements ErrorCode {

    /**
     * 通知不存在，或不属于当前用户。
     */
    NOTIFICATION_NOT_FOUND(50001, "通知不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    NotificationErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
