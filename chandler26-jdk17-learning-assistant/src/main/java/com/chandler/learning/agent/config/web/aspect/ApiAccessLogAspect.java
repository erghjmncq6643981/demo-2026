package com.chandler.learning.agent.config.web.aspect;

import com.chandler.learning.agent.config.web.annotation.ApiAccessLog;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.security.CurrentUserContext;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 统一记录 Controller 业务访问摘要，避免各接口重复编写计时和错误日志。
 * 只提取分页元数据，不读取或序列化响应中的文章、词卡、Prompt 等内容。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiAccessLogAspect {

    private final CurrentUserContext currentUserContext;

    /** 记录 Controller 调用结果、耗时、追踪标识和有限分页元数据。 */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        HttpServletRequest request = currentRequest();
        String operation = operationName(joinPoint);
        String method = request == null ? "-" : request.getMethod();
        String path = request == null ? "-" : request.getRequestURI();
        String currentUserId = userId();
        String currentTraceId = traceId();
        try {
            Object result = joinPoint.proceed();
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((value, error) -> logCompletion(operation, method, path, currentUserId,
                        currentTraceId, startedAt, value, error));
            } else {
                log.info("event=api_access operation={} method={} path={} userId={} traceId={} costMs={} success=true errorCode=- pagination={}",
                        operation, method, path, currentUserId, currentTraceId, elapsedMs(startedAt), pagination(result));
            }
            return result;
        } catch (Throwable error) {
            String errorCode = error instanceof LearningAssistantException business
                    ? business.getErrorCode() : error.getClass().getSimpleName();
            log.info("event=api_access operation={} method={} path={} userId={} traceId={} costMs={} success=false errorCode={} pagination={}",
                    operation, method, path, currentUserId, currentTraceId, elapsedMs(startedAt),
                    errorCode, Map.of());
            throw error;
        }
    }

    /** CompletionStage 返回值在异步完成时记录真实耗时，避免把排队时间和业务执行结果丢失。 */
    private void logCompletion(String operation, String method, String path, String userId, String traceId,
                               long startedAt, Object value, Throwable error) {
        if (error == null) {
            log.info("event=api_access operation={} method={} path={} userId={} traceId={} costMs={} success=true errorCode=- pagination={}",
                    operation, method, path, userId, traceId, elapsedMs(startedAt), pagination(value));
            return;
        }
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String errorCode = cause instanceof LearningAssistantException business
                ? business.getErrorCode() : cause.getClass().getSimpleName();
        log.info("event=api_access operation={} method={} path={} userId={} traceId={} costMs={} success=false errorCode={} pagination={}",
                operation, method, path, userId, traceId, elapsedMs(startedAt), errorCode, Map.of());
    }

    private String operationName(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        ApiAccessLog methodAnnotation = method.getAnnotation(ApiAccessLog.class);
        if (methodAnnotation != null && !methodAnnotation.value().isBlank()) return methodAnnotation.value();
        ApiAccessLog typeAnnotation = joinPoint.getTarget().getClass().getAnnotation(ApiAccessLog.class);
        if (typeAnnotation != null && !typeAnnotation.value().isBlank()) return typeAnnotation.value();
        Operation operation = method.getAnnotation(Operation.class);
        if (operation != null && !operation.summary().isBlank()) return operation.summary();
        return method.getName();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private String userId() {
        return currentUserContext.findUser()
                .map(user -> String.valueOf(user.getId()))
                .orElse("-");
    }

    private String traceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "-" : traceId;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private Map<String, Object> pagination(Object result) {
        if (result == null) return Map.of();
        if (result instanceof Collection<?> collection) return Map.of("returned", collection.size());
        Map<String, Object> metadata = new LinkedHashMap<>();
        putNumber(metadata, result, "page");
        putNumber(metadata, result, "pageSize");
        putNumber(metadata, result, "total");
        putNumber(metadata, result, "filteredTotal");
        return metadata;
    }

    private void putNumber(Map<String, Object> target, Object source, String property) {
        try {
            Method getter = source.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1));
            Object value = getter.invoke(source);
            if (value instanceof Number) target.put(property, value);
        } catch (ReflectiveOperationException ignored) {
            // Response DTOs without pagination metadata are ordinary non-page responses.
        }
    }
}
