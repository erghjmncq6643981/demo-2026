package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.SystemLogRequest;
import com.chandler.learning.agent.domain.dto.learning.SystemLogResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningSystemLog;
import com.chandler.learning.agent.mapper.learning.LearningSystemLogMapper;
import com.chandler.learning.agent.security.LearningUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final LearningSystemLogMapper systemLogMapper;

    public void record(Long userId, String type, String title, String detail) {
        SystemLogRequest request = new SystemLogRequest();
        request.setType(type);
        request.setTitle(title);
        request.setDetail(detail);
        request.setSource("server");
        create(userId, request);
    }

    public SystemLogResponse create(Long userId, SystemLogRequest request) {
        Long resolvedUserId = userId == null ? currentUserId() : userId;
        if (resolvedUserId == null) {
            return null;
        }
        LearningSystemLog log = new LearningSystemLog();
        log.setUserId(resolvedUserId);
        log.setLogType(trimOrDefault(request.getType(), "system"));
        log.setTitle(trimOrDefault(request.getTitle(), "系统日志"));
        log.setDetail(trimToNull(request.getDetail()));
        log.setSource(trimOrDefault(request.getSource(), "client"));
        log.setBusinessType(trimToNull(request.getBusinessType()));
        log.setBusinessId(trimToNull(request.getBusinessId()));
        log.setCreateTime(LocalDateTime.now());
        systemLogMapper.insert(log);
        return toResponse(log);
    }

    public List<SystemLogResponse> list(Long userId, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 200));
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
