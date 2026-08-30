package com.chandler.learning.agent.config.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个 HTTP 请求生成 traceId，并输出统一的访问日志。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String REQUEST_ID = "x-request-id";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 处理当前请求并维护认证或追踪上下文。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        String requestId = resolveRequestId(request, traceId);
        long startTime = System.currentTimeMillis();
        MDC.put(TRACE_ID, traceId);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(TRACE_HEADER, traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            log.debug("HTTP {} {} status={} cost={}ms client={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    costTime,
                    clientIp(request));
            MDC.remove(TRACE_ID);
            MDC.remove(REQUEST_ID);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String headerTraceId = request.getHeader(TRACE_HEADER);
        if (StringUtils.hasText(headerTraceId)) {
            return headerTraceId.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveRequestId(HttpServletRequest request, String traceId) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? requestId.trim() : traceId;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }
}
