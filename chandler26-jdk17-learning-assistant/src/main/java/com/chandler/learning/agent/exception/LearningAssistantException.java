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

    private LearningAssistantException(HttpStatus status, String errorCode, String message,
                                       String debugMessage, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.debugMessage = debugMessage;
    }

    public static LearningAssistantException badRequest(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.BAD_REQUEST, errorCode, message, message, null);
    }

    public static LearningAssistantException unauthorized(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.UNAUTHORIZED, errorCode, message, message, null);
    }

    public static LearningAssistantException notFound(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.NOT_FOUND, errorCode, message, message, null);
    }

    public static LearningAssistantException system(String errorCode, String message, Throwable cause) {
        String debugMessage = cause == null ? message : cause.getMessage();
        return new LearningAssistantException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message, debugMessage, cause);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDebugMessage() {
        return debugMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
