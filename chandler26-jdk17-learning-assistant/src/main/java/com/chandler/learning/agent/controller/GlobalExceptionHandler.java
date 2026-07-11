package com.chandler.learning.agent.controller;

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

    private final ObjectMapper objectMapper;

    @ExceptionHandler(LearningAssistantException.class)
    public ResponseEntity<Map<String, String>> handleLearningAssistant(LearningAssistantException ex) {
        log.warn("业务异常 errorCode={} message={} debug={}",
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getDebugMessage(),
                ex);
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
                "message", ex.getMessage(),
                "errorCode", ex.getErrorCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("请求参数未通过校验: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "参数错误" : error.getDefaultMessage())
                .orElse("参数错误");
        log.warn("请求参数绑定失败: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraint(ConstraintViolationException ex) {
        log.warn("请求参数约束失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleExternalService(RestClientResponseException ex) {
        String upstreamMessage = readUpstreamMessage(ex.getResponseBodyAsString());
        String message = StringUtils.hasText(upstreamMessage)
                ? "外部服务调用失败（HTTP " + ex.getStatusCode().value() + "）：" + upstreamMessage
                : "外部服务调用失败（HTTP " + ex.getStatusCode().value() + "）";
        log.warn("外部服务调用失败 status={} message={} body={}",
                ex.getStatusCode().value(),
                upstreamMessage,
                ex.getResponseBodyAsString(),
                ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "message", message,
                "errorCode", LearningConstants.ErrorCode.EXTERNAL_SERVICE_CALL_FAILED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("系统发生未预期异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "系统异常，请稍后重试",
                "errorCode", LearningConstants.ErrorCode.SYSTEM_UNEXPECTED));
    }

    private String readUpstreamMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText(null);
            if (StringUtils.hasText(message)) {
                return message;
            }
            message = root.path("message").asText(null);
            return StringUtils.hasText(message) ? message : responseBody;
        } catch (Exception ignored) {
            return responseBody;
        }
    }
}
