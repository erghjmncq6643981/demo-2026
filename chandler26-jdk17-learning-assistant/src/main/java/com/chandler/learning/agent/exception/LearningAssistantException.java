package com.chandler.learning.agent.exception;

import org.springframework.http.HttpStatus;

/**
 * 学习助手统一运行时异常。
 * <p>
 * message 面向前端和业务人员；errorCode 与 debugMessage 面向技术排查和日志检索。
 */
public class LearningAssistantException extends RuntimeException {

    private final String errorCode;
    private final String debugMessage;
    private final HttpStatus status;

    /**
     * 处理 {@code LearningAssistantException} 相关业务。
     */
    private LearningAssistantException(HttpStatus status, String errorCode, String message,
                                       String debugMessage, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.debugMessage = debugMessage;
    }

    /**
     * 处理 {@code badRequest} 相关业务。
     */
    public static LearningAssistantException badRequest(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.BAD_REQUEST, errorCode, message, message, null);
    }

    /**
     * 处理 {@code unauthorized} 相关业务。
     */
    public static LearningAssistantException unauthorized(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.UNAUTHORIZED, errorCode, message, message, null);
    }

    /**
     * 处理 {@code notFound} 相关业务。
     */
    public static LearningAssistantException notFound(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.NOT_FOUND, errorCode, message, message, null);
    }

    /**
     * 处理 {@code externalService} 相关业务。
     */
    public static LearningAssistantException externalService(String errorCode, String message, Throwable cause) {
        String debugMessage = cause == null ? message : cause.getMessage();
        return new LearningAssistantException(HttpStatus.BAD_GATEWAY, errorCode, message, debugMessage, cause);
    }

    /**
     * 处理 {@code system} 相关业务。
     */
    public static LearningAssistantException system(String errorCode, String message, Throwable cause) {
        String debugMessage = cause == null ? message : cause.getMessage();
        return new LearningAssistantException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message, debugMessage, cause);
    }

    /**
     * 查询 {@code getErrorCode} 相关业务。
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 查询 {@code getDebugMessage} 相关业务。
     */
    public String getDebugMessage() {
        return debugMessage;
    }

    /**
     * 查询 {@code getStatus} 相关业务。
     */
    public HttpStatus getStatus() {
        return status;
    }
}
