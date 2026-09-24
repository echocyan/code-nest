package com.echocyan.codenest.social;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * social 模块错误码，号段 4xxxx。
 */
public enum SocialErrorCode implements ErrorCode {

    CANNOT_FOLLOW_SELF(40001, "不能关注自己", 400),
    USER_NOT_FOUND(40002, "用户不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    SocialErrorCode(int code, String message, int httpStatus) {
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
