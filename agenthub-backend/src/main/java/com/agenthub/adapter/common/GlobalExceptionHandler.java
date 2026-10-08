package com.agenthub.adapter.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(com.agenthub.shared.exception.BusinessException.class)
    public Result<Void> handleSharedBusinessException(com.agenthub.shared.exception.BusinessException ex) {
        log.warn("Business exception occurred: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return Result.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        log.warn("Business exception occurred: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        return Result.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(Exception ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "Invalid request parameter format");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGenericException(Exception ex) {
        log.error("Unhandled system exception: ", ex);
        return Result.fail(ErrorCode.INTERNAL_SERVER_ERROR, "Internal system error occurred");
    }
}
