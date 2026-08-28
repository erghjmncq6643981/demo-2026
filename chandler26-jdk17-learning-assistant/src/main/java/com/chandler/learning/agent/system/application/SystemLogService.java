package com.chandler.learning.agent.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.system.api.response.SystemLogResponse;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLog;
import com.chandler.learning.agent.system.domain.entity.LearningSystemLogOutbox;
import com.chandler.learning.agent.system.domain.enums.SystemLogOutboxStatus;
import com.chandler.learning.agent.system.domain.enums.SystemLogSource;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogMapper;
import com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogOutboxMapper;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.system.domain.constant.SystemLogConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统日志服务。
 * <p>
 * 这里保存的是可在产品界面查看的业务日志；运行时诊断日志仍由 SLF4J/Logback 写入控制台和文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final LearningSystemLogMapper systemLogMapper;
    private final LearningSystemLogOutboxMapper systemLogOutboxMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserContext currentUserContext;

    /**
     * 更新 {@code record} 相关业务。
     */
    public void record(Long userId, SystemLogType type, String title, String detail) {
        record(userId, type == null ? null : type.getCode(), title, detail);
    }

    /**
     * 更新 {@code record} 相关业务。
     */
    public void record(Long userId, String type, String title, String detail) {
        publish(userId, type, title, detail, SystemLogSource.SERVER.getCode(), null, null);
    }

    /**
     * 接收前端交互日志。来源由服务端固定为 {@code client}，避免客户端伪造服务端业务日志。
     */
    public void recordClient(Long userId, String type, String title, String detail,
                             String businessType, String businessId) {
        publish(userId, type, title, detail, SystemLogSource.CLIENT.getCode(), businessType, businessId);
    }

    private void publish(Long userId, String type, String title, String detail, String source,
                         String businessType, String businessId) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            log.debug("event=system_log_persistence result=ignored reason=missing_user type={} title={}", type, title);
            return;
        }
        LearningSystemLogOutbox outbox = new LearningSystemLogOutbox();
        outbox.setUserId(resolvedUserId);
        outbox.setLogType(SystemLogType.of(type).getCode());
        outbox.setTitle(trimOrDefault(title, SystemLogConstants.DEFAULT_TITLE,
                SystemLogConstants.MAX_TITLE_LENGTH));
        outbox.setDetail(trimToNull(detail, SystemLogConstants.MAX_DETAIL_LENGTH));
        outbox.setSource(SystemLogSource.of(source).getCode());
        outbox.setBusinessType(trimToNull(businessType, SystemLogConstants.MAX_BUSINESS_TYPE_LENGTH));
        outbox.setBusinessId(trimToNull(businessId, SystemLogConstants.MAX_BUSINESS_ID_LENGTH));
        outbox.setOccurredAt(LocalDateTime.now());
        outbox.setTraceId(currentTraceId());
        outbox.setStatus(SystemLogOutboxStatus.PENDING.getCode());
        try {
            systemLogOutboxMapper.insert(outbox);
            eventPublisher.publishEvent(new SystemLogRecordedEvent(
                    outbox.getId(), outbox.getUserId(), outbox.getLogType(), outbox.getTitle(), outbox.getDetail(),
                    outbox.getSource(), outbox.getBusinessType(), outbox.getBusinessId(), outbox.getOccurredAt(),
                    outbox.getTraceId()));
        } catch (RuntimeException ex) {
            log.warn("event=system_log_outbox result=failed userId={} type={} error={}",
                    resolvedUserId, outbox.getLogType(), ex.getClass().getSimpleName());
            log.debug("系统日志 Outbox 写入或发布失败 userId={} type={}", resolvedUserId, outbox.getLogType(), ex);
        }
    }

    /**
     * 查询 {@code list} 相关业务。
     */
    public List<SystemLogResponse> list(Long userId, int limit) {
        int resolvedLimit = Math.max(SystemLogConstants.MIN_LIMIT, Math.min(limit, SystemLogConstants.MAX_LIMIT));
        return systemLogMapper.selectList(new LambdaQueryWrapper<LearningSystemLog>()
                        .eq(LearningSystemLog::getUserId, userId)
                        .orderByDesc(LearningSystemLog::getCreateTime)
                        .last("LIMIT " + resolvedLimit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 更新 {@code clear} 相关业务。
     */
    public void clear(Long userId) {
        systemLogMapper.delete(new LambdaQueryWrapper<LearningSystemLog>()
                .eq(LearningSystemLog::getUserId, userId));
        log.debug("用户系统日志已清理 userId={}", userId);
    }

    /**
     * 转换 {@code toResponse} 相关业务。
     */
    private SystemLogResponse toResponse(LearningSystemLog log) {
        SystemLogResponse response = new SystemLogResponse();
        response.setId(log.getId());
        response.setType(log.getLogType());
        response.setTitle(log.getTitle());
        response.setDetail(log.getDetail());
        response.setSource(log.getSource());
        response.setBusinessType(log.getBusinessType());
        response.setBusinessId(log.getBusinessId());
        response.setTime(log.getCreateTime());
        return response;
    }

    /**
     * 处理 {@code trimToNull} 相关业务。
     */
    private String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /**
     * 处理 {@code trimOrDefault} 相关业务。
     */
    private String trimOrDefault(String value, String fallback, int maxLength) {
        String normalized = trimToNull(value, maxLength);
        return normalized == null ? fallback : normalized;
    }

    private String currentTraceId() {
        String traceId = trimToNull(MDC.get("traceId"), SystemLogConstants.MAX_TRACE_ID_LENGTH);
        return traceId == null ? "-" : traceId;
    }

    /**
     * 处理 {@code currentUserId} 相关业务。
     */
    private Long currentUserId() {
        return currentUserContext.findUser().map(user -> user.getId()).orElse(null);
    }
}
