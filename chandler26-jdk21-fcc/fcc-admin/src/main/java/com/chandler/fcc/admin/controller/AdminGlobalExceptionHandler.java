package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 管理台统一全局异常处理器
 * <p>
 * 统一拦截参数校验失败、白名单准入受限及未知异常并封装为规范的 CommonResult 返回。
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
        log.warn("⚠️ [Admin] 请求参数或白名单准入校验未通过: {}", e.getMessage());
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
