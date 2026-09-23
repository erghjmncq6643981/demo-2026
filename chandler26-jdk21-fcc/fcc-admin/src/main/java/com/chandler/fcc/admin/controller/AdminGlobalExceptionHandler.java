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

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.springframework.dao.DuplicateKeyException;
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
     * 数据库唯一键冲突拦截 (如工号重复创建等)
     *
     * @param e 唯一键冲突异常
     * @return 400 业务错误响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("⚠️ [Admin] 数据库唯一约束冲突: {}", e.getMessage());
        return CommonResult.error(400, "操作失败：目标工号或关键标识已存在，请勿重复创建");
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
     * Sa-Token 未登录或凭据失效拦截 (返回标准 401 Unauthorized)
     *
     * @param e 未登录异常对象
     * @return 401 统一响应
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CommonResult<Void> handleNotLogin(NotLoginException e) {
        log.warn("⚠️ [Admin] 请求未通过登录认证: type={}, msg={}", e.getType(), e.getMessage());
        return CommonResult.error(401, "登录已失效，请重新登录");
    }

    /**
     * Sa-Token 角色或权限不足拦截 (返回 403 Forbidden)
     *
     * @param e 权限异常对象
     * @return 403 统一响应
     */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResult<Void> handleNotPermission(Exception e) {
        log.warn("⚠️ [Admin] 用户操作权限不足: {}", e.getMessage());
        return CommonResult.error(403, "没有操作该资源的权限");
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
