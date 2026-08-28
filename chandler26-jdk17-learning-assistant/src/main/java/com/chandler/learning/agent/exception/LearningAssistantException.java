package com.chandler.learning.agent.exception;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
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

    /** 使用错误码默认 HTTP 状态和中文提示创建业务异常。 */
    public static LearningAssistantException badRequest(LearningErrorCode code) {
        return from(code, code.getDefaultMessage(), null);
    }

    /** 使用错误码并覆盖用户提示；覆盖消息只用于确实需要上下文的场景。 */
    public static LearningAssistantException badRequest(LearningErrorCode code, String message) {
        return from(code, message, null);
    }

    /**
     * 处理 {@code unauthorized} 相关业务。
     */
    public static LearningAssistantException unauthorized(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.UNAUTHORIZED, errorCode, message, message, null);
    }

    /** 使用错误码默认提示创建未授权异常。 */
    public static LearningAssistantException unauthorized(LearningErrorCode code) {
        return from(code, code.getDefaultMessage(), null);
    }

    /** 使用错误码并覆盖用户提示创建未授权异常。 */
    public static LearningAssistantException unauthorized(LearningErrorCode code, String message) {
        return from(code, message, null);
    }

    /**
     * 处理 {@code notFound} 相关业务。
     */
    public static LearningAssistantException notFound(String errorCode, String message) {
        return new LearningAssistantException(HttpStatus.NOT_FOUND, errorCode, message, message, null);
    }

    /** 使用错误码默认提示创建资源不存在异常。 */
    public static LearningAssistantException notFound(LearningErrorCode code) {
        return from(code, code.getDefaultMessage(), null);
    }

    /** 使用错误码并覆盖用户提示创建资源不存在异常。 */
    public static LearningAssistantException notFound(LearningErrorCode code, String message) {
        return from(code, message, null);
    }

    /**
     * 处理 {@code externalService} 相关业务。
     */
    public static LearningAssistantException externalService(String errorCode, String message, Throwable cause) {
        String debugMessage = cause == null ? message : cause.getMessage();
        return new LearningAssistantException(HttpStatus.BAD_GATEWAY, errorCode, message, debugMessage, cause);
    }

    /** 使用错误码记录外部服务异常，保留底层异常作为技术诊断信息。 */
    public static LearningAssistantException externalService(LearningErrorCode code,
                                                             String message, Throwable cause) {
        return from(code, message, cause);
    }

    /**
     * 处理 {@code system} 相关业务。
     */
    public static LearningAssistantException system(String errorCode, String message, Throwable cause) {
        String debugMessage = cause == null ? message : cause.getMessage();
        return new LearningAssistantException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message, debugMessage, cause);
    }

    /** 使用错误码记录系统异常，默认提示不暴露技术细节。 */
    public static LearningAssistantException system(LearningErrorCode code,
                                                    String message, Throwable cause) {
        return from(code, message, cause);
    }

    /** 按错误码定义的默认 HTTP 状态和中文提示创建异常。 */
    public static LearningAssistantException of(LearningErrorCode code) {
        return new LearningAssistantException(code.getStatus(), code.getCode(), code.getDefaultMessage(),
                code.getDefaultMessage(), null);
    }

    /** 按错误码默认定义创建异常，并保留底层原因。 */
    public static LearningAssistantException of(LearningErrorCode code, Throwable cause) {
        String debugMessage = cause == null ? code.getDefaultMessage() : cause.getMessage();
        return new LearningAssistantException(code.getStatus(), code.getCode(), code.getDefaultMessage(),
                debugMessage, cause);
    }

    private static LearningAssistantException from(LearningErrorCode code, String message, Throwable cause) {
        String resolvedMessage = message == null ? code.getDefaultMessage() : message;
        String debugMessage = cause == null ? resolvedMessage : cause.getMessage();
        return new LearningAssistantException(code.getStatus(), code.getCode(), resolvedMessage, debugMessage, cause);
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
