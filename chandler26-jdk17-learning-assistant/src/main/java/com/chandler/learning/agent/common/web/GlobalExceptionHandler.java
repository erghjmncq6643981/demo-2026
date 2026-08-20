package com.chandler.learning.agent.common.web;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * 统一异常出口，保证前端拿到稳定的 JSON 错误结构。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final int MAX_UPSTREAM_MESSAGE_LENGTH = 500;

    private final ObjectMapper objectMapper;

    /**
     * 处理 {@code handleLearningAssistant} 相关业务。
     */
    @ExceptionHandler(LearningAssistantException.class)
    public ResponseEntity<Map<String, String>> handleLearningAssistant(LearningAssistantException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("event=business_error errorCode={} message={}", ex.getErrorCode(), ex.getMessage());
            log.debug("业务异常技术堆栈 errorCode={}", ex.getErrorCode(), ex);
        } else {
            log.info("event=business_rejected errorCode={} message={}", ex.getErrorCode(), ex.getMessage());
            log.debug("业务异常诊断 errorCode={} debugMessage={}", ex.getErrorCode(), ex.getDebugMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "message", ex.getMessage(),
                "errorCode", ex.getErrorCode()));
    }

    /**
     * 处理 {@code handleIllegalArgument} 相关业务。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数未通过校验: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    /**
     * 处理 {@code handleValidation} 相关业务。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
                .orElse("参数错误");
        log.warn("请求参数绑定失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    /**
     * 处理 {@code handleConstraint} 相关业务。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraint(ConstraintViolationException ex) {
        log.warn("请求参数约束失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    /**
     * 处理 {@code handleExternalService} 相关业务。
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleExternalService(RestClientResponseException ex) {
        String upstreamMessage = readUpstreamMessage(ex.getResponseBodyAsString());
        String message = StringUtils.hasText(upstreamMessage)
                ? "外部服务调用失败（HTTP " + ex.getStatusCode().value() + "）：" + upstreamMessage
                : "外部服务调用失败（HTTP " + ex.getStatusCode().value() + "）";
        log.warn("event=external_service_failed status={} message={}",
                ex.getStatusCode().value(),
                upstreamMessage);
        log.debug("外部服务异常技术堆栈 status={}", ex.getStatusCode().value(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "message", message,
                "errorCode", LearningConstants.ErrorCode.EXTERNAL_SERVICE_CALL_FAILED.getCode()));
    }

    /**
     * 处理 {@code handleUnexpected} 相关业务。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("系统发生未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "系统异常，请稍后重试",
                "errorCode", LearningConstants.ErrorCode.SYSTEM_UNEXPECTED.getCode()));
    }

    /**
     * 查询 {@code readUpstreamMessage} 相关业务。
     */
    private String readUpstreamMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return truncate(message);
            }
            message = root.path("message").asText(null);
            return truncate(StringUtils.hasText(message) ? message : responseBody);
        } catch (Exception ignored) {
            return truncate(responseBody);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_UPSTREAM_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_UPSTREAM_MESSAGE_LENGTH);
    }
}
