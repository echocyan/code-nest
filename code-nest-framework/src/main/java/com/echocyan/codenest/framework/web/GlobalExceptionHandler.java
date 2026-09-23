package com.echocyan.codenest.framework.web;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.exception.ErrorCode;
import com.echocyan.codenest.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return respond(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        return error == null ? respond(CommonErrorCode.BAD_REQUEST)
                : respond(CommonErrorCode.BAD_REQUEST, error.getField() + ": " + error.getDefaultMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        if (e.isForReturnValue()) {
            // 返回值校验失败是服务端的问题
            return handleUnexpected(e);
        }
        return e.getAllErrors().stream().findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .map(message -> respond(CommonErrorCode.BAD_REQUEST, message))
                .orElseGet(() -> respond(CommonErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return e.getConstraintViolations().stream().findFirst()
                .map(ConstraintViolation::getMessage)
                .map(message -> respond(CommonErrorCode.BAD_REQUEST, message))
                .orElseGet(() -> respond(CommonErrorCode.BAD_REQUEST));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return respond(CommonErrorCode.BAD_REQUEST);
    }

    /**
     * Spring MVC 自带的请求类异常（路径不存在、方法不支持、Content-Type 不支持、缺少参数或请求头等）
     * 都实现了 {@link ErrorResponse}：沿用其 4xx 状态码，body 里的 code 取最接近的通用错误码。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            CommonErrorCode errorCode = status.value() == 404 ? CommonErrorCode.NOT_FOUND : CommonErrorCode.BAD_REQUEST;
            return ResponseEntity.status(status).body(Result.fail(errorCode, errorCode.message()));
        }
        return handleUnexpected(e);
    }

    private static ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode) {
        return respond(errorCode, errorCode.message());
    }

    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.fail(errorCode, message));
    }
}
