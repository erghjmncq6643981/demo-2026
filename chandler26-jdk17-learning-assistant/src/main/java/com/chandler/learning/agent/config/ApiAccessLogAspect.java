package com.chandler.learning.agent.config;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一记录 Controller 业务访问摘要，避免各接口重复编写计时和错误日志。
 * 只提取分页元数据，不读取或序列化响应中的文章、词卡、Prompt 等内容。
 */
@Aspect
@Component
@Slf4j
public class ApiAccessLogAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        HttpServletRequest request = currentRequest();
        String operation = operationName(joinPoint);
        try {
            Object result = joinPoint.proceed();
            log.info("业务接口访问 operation={} method={} path={} userId={} traceId={} costMs={} success=true errorCode=- pagination={}",
                    operation, request == null ? "-" : request.getMethod(),
                    request == null ? "-" : request.getRequestURI(), userId(), traceId(), elapsedMs(startedAt),
                    pagination(result));
            return result;
        } catch (Throwable error) {
            String errorCode = error instanceof LearningAssistantException business
                    ? business.getErrorCode() : error.getClass().getSimpleName();
            log.info("业务接口访问 operation={} method={} path={} userId={} traceId={} costMs={} success=false errorCode={} pagination={}",
                    operation, request == null ? "-" : request.getMethod(),
                    request == null ? "-" : request.getRequestURI(), userId(), traceId(), elapsedMs(startedAt),
                    errorCode, Map.of());
            throw error;
        }
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LearningUserPrincipal principal
                && principal.user().getId() != null) {
            return String.valueOf(principal.user().getId());
        }
        return "-";
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
