package com.echocyan.codenest.user;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * user 模块错误码，号段 1xxxx。
 */
public enum UserErrorCode implements ErrorCode {

    USERNAME_TAKEN(10001, "用户名已被占用", 409),
    BAD_CREDENTIALS(10002, "用户名或密码错误", 401),
    USER_NOT_FOUND(10003, "用户不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    UserErrorCode(int code, String message, int httpStatus) {
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
