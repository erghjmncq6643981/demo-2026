package com.chandler.motivation.common.exception;

import com.chandler.motivation.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MotivationException.class)
    public ApiResponse<Void> handleMotivationException(MotivationException ex) {
        return ApiResponse.fail(400, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.fail(500, "系统繁忙，请稍后重试");
    }
}
