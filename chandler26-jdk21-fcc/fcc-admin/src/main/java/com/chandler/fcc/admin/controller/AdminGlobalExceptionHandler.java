package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * 管理台统一全局异常处理器
 * <p>
 * 统一拦截参数校验失败、业务约束异常及未知异常并封装为规范的 CommonResult 返回。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@RestControllerAdvice
public class AdminGlobalExceptionHandler {

    /**
     * 业务断言与非法参数异常处理
     *
     * @param e 异常对象
     * @return 错误响应实体
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("⚠️ [Admin] 请求参数或业务约束校验未通过: {}", e.getMessage());
        return CommonResult.error(400, e.getMessage());
    }

    /**
     * 入参 JSR-303 注解校验异常处理
     *
     * @param e 校验异常
     * @return 错误响应实体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("⚠️ [Admin] 参数校验失败: {}", msg);
        return CommonResult.error(400, msg);
    }

    /**
     * 保留下游业务服务返回的 HTTP 状态与可读提示。
     *
     * @param e 带 HTTP 状态的业务异常
     * @return 对应状态的统一错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CommonResult<Void>> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        CommonResult<Void> body = CommonResult.error(
            status,
            e.getReason() == null ? "请求处理失败" : e.getReason()
        );
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    /**
     * 未知运行时全局异常处理
     *
     * @param e 异常对象
     * @return 错误响应实体
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResult<Void> handleGeneralException(Exception e) {
        log.error("❌ [Admin] 系统处理发生未捕获异常", e);
        return CommonResult.error(500, "系统内部繁忙，请稍后重试: " + e.getMessage());
    }
}
