package com.echocyan.codenest.common.exception;

/**
 * 通用错误码，后三位与 HTTP 状态码一致。
 */
public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST(90400, "请求参数错误", 400),
    UNAUTHORIZED(90401, "未登录或登录已失效", 401),
    FORBIDDEN(90403, "无权限执行该操作", 403),
    NOT_FOUND(90404, "资源不存在", 404),
    TOO_MANY_REQUESTS(90429, "请求过于频繁", 429),
    INTERNAL_ERROR(99999, "系统繁忙，请稍后再试", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(int code, String message, int httpStatus) {
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
