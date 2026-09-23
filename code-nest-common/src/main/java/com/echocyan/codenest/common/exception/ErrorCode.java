package com.echocyan.codenest.common.exception;

/**
 * 错误码。万位是模块编号（1 user、2 article……9 通用），各模块用一个枚举实现本接口。
 */
public interface ErrorCode {

    int code();

    String message();

    int httpStatus();
}
