package com.echocyan.codenest.common.result;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 统一返回体。成功时 code 为 0；失败时 code 为业务错误码，HTTP 状态码另行按语义设置。
 */
public record Result<T>(int code, String message, T data) {

    public static final int SUCCESS_CODE = 0;

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, "ok", data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static Result<Void> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }
}
