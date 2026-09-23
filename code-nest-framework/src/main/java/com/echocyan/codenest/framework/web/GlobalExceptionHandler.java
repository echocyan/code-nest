package com.echocyan.codenest.framework.web;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.exception.ErrorCode;
import com.echocyan.codenest.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        String message = error == null ? CommonErrorCode.BAD_REQUEST.message()
                : error.getField() + ": " + error.getDefaultMessage();
        return respond(CommonErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(CommonErrorCode.BAD_REQUEST.message());
        return respond(CommonErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse(CommonErrorCode.BAD_REQUEST.message());
        return respond(CommonErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return respond(CommonErrorCode.BAD_REQUEST, CommonErrorCode.BAD_REQUEST.message());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return respond(CommonErrorCode.NOT_FOUND, CommonErrorCode.NOT_FOUND.message());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.message());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.message());
    }

    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.fail(errorCode, message));
    }
}
