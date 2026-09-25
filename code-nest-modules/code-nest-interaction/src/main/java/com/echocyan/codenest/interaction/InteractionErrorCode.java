package com.echocyan.codenest.interaction;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * interaction 模块错误码，号段 3xxxx。
 */
public enum InteractionErrorCode implements ErrorCode {

    /**
     * 文章不存在、已删除或还是草稿。
     */
    ARTICLE_NOT_FOUND(30001, "文章不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    InteractionErrorCode(int code, String message, int httpStatus) {
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
