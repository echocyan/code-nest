package com.echocyan.codenest.common.exception;

/**
 * 业务异常，由全局异常处理转换为对应的 HTTP 状态码和返回体。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
