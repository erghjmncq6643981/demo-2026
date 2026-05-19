package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.SystemLogRequest;
import com.chandler.learning.agent.domain.dto.learning.SystemLogResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningSystemLog;
import com.chandler.learning.agent.mapper.learning.LearningSystemLogMapper;
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

    public void record(Long userId, String type, String title, String detail) {
        SystemLogRequest request = new SystemLogRequest();
        request.setType(type);
        request.setTitle(title);
        request.setDetail(detail);
        request.setSource(LearningConstants.SystemLog.SOURCE_SERVER);
        create(userId, request);
    }

    public SystemLogResponse create(Long userId, SystemLogRequest request) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            return null;
        }
        LearningSystemLog entity = new LearningSystemLog();
        entity.setUserId(resolvedUserId);
        entity.setLogType(trimOrDefault(request.getType(), LearningConstants.SystemLog.DEFAULT_TYPE));
        entity.setTitle(trimOrDefault(request.getTitle(), LearningConstants.SystemLog.DEFAULT_TITLE));
        entity.setDetail(trimToNull(request.getDetail()));
        entity.setSource(trimOrDefault(request.getSource(), LearningConstants.SystemLog.SOURCE_CLIENT));
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

    public void clear(Long userId) {
        systemLogMapper.delete(new LambdaQueryWrapper<LearningSystemLog>()
                .eq(LearningSystemLog::getUserId, userId));
        log.debug("用户系统日志已清理 userId={}", userId);
    }

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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

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
