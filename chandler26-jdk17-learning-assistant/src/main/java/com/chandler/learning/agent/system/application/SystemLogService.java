package com.chandler.learning.agent.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.system.api.SystemLogRequest;
import com.chandler.learning.agent.system.api.SystemLogResponse;
import com.chandler.learning.agent.system.domain.LearningSystemLog;
import com.chandler.learning.agent.system.domain.SystemLogSource;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.system.infrastructure.LearningSystemLogMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    /**
     * 更新 {@code record} 相关业务。
     */
    public void record(Long userId, SystemLogType type, String title, String detail) {
        record(userId, type.getCode(), title, detail);
    }

    /**
     * 更新 {@code record} 相关业务。
     */
    public void record(Long userId, String type, String title, String detail) {
        SystemLogRequest request = new SystemLogRequest();
        request.setType(SystemLogType.of(type).getCode());
        request.setTitle(title);
        request.setDetail(detail);
        request.setSource(SystemLogSource.SERVER.getCode());
        create(userId, request);
    }

    /**
     * 创建或保存 {@code create} 相关业务。
     */
    public SystemLogResponse create(Long userId, SystemLogRequest request) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            return null;
        }
        LearningSystemLog entity = new LearningSystemLog();
        entity.setUserId(resolvedUserId);
        entity.setLogType(SystemLogType.of(request.getType()).getCode());
        entity.setTitle(trimOrDefault(request.getTitle(), LearningConstants.SystemLog.DEFAULT_TITLE));
        entity.setDetail(trimToNull(request.getDetail()));
        entity.setSource(SystemLogSource.of(request.getSource()).getCode());
        entity.setBusinessType(trimToNull(request.getBusinessType()));
        entity.setBusinessId(trimToNull(request.getBusinessId()));
        entity.setCreateTime(LocalDateTime.now());
        systemLogMapper.insert(entity);
        log.debug("系统日志已入库 userId={} type={} title={} businessType={} businessId={}",
                resolvedUserId,
                entity.getLogType(),
                entity.getTitle(),
                entity.getBusinessType(),
                entity.getBusinessId());
        return toResponse(entity);
    }

    /**
     * 查询 {@code list} 相关业务。
     */
    public List<SystemLogResponse> list(Long userId, int limit) {
        int resolvedLimit = Math.max(LearningConstants.SystemLog.MIN_LIMIT, Math.min(limit, LearningConstants.SystemLog.MAX_LIMIT));
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
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 处理 {@code trimOrDefault} 相关业务。
     */
    private String trimOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /**
     * 处理 {@code currentUserId} 相关业务。
     */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof LearningUserPrincipal principal) {
            return principal.user().getId();
        }
        return null;
    }
}
