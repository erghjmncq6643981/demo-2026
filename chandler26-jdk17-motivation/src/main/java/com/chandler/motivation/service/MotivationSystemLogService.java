package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dataobject.MotivationSystemLog;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.dto.log.ChildActivityLogResponse;
import com.chandler.motivation.domain.mapper.MotivationChildMapper;
import com.chandler.motivation.domain.mapper.MotivationSystemLogMapper;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotivationSystemLogService extends ServiceImpl<MotivationSystemLogMapper, MotivationSystemLog> {

    private static final String DEFAULT_OPERATOR = "系统";
    private static final DateTimeFormatter BUSINESS_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MotivationUserMapper userMapper;
    private final MotivationChildMapper childMapper;

    /**
     * 记录可给业务人员阅读的动作日志。
     */
    public void record(Long userId, Long childId, String logType, String title, String detail) {
        recordBusiness(userId, childId,
                MotivationEnums.fromCode(MotivationEnums.LogType.class, logType, MotivationEnums.LogType.SYSTEM),
                title,
                detail);
    }

    /**
     * 记录可给业务人员阅读的动作日志，格式统一为“谁在什么时间做了什么”。
     */
    public void recordBusiness(Long userId,
                               Long childId,
                               MotivationEnums.LogType logType,
                               String title,
                               String detail) {
        LocalDateTime now = LocalDateTime.now();
        String operatorName = resolveOperatorName(userId);
        String businessTitle = trimOrDefault(title, "业务记录");
        String businessDetail = businessDetail(operatorName, now, trimOrDefault(detail, businessTitle));
        saveLog(userId, childId, logType, businessTitle, businessDetail, MotivationEnums.LogSource.BUSINESS, now);
        log.info(businessDetail);
    }

    /**
     * 记录服务端诊断日志，只用于排查系统运行状态。
     */
    public void recordSystem(Long userId,
                             Long childId,
                             MotivationEnums.LogType logType,
                             String title,
                             String detail) {
        LocalDateTime now = LocalDateTime.now();
        String systemTitle = trimOrDefault(title, "系统记录");
        String systemDetail = trimOrDefault(detail, systemTitle);
        saveLog(userId, childId, logType, systemTitle, systemDetail, MotivationEnums.LogSource.SYSTEM, now);
        log.debug("系统日志已记录 userId={} childId={} type={} title={} detail={}",
                userId, childId, logType.code(), systemTitle, systemDetail);
    }

    /**
     * 查询当前账号可见孩子的成长活动日志。
     */
    public List<ChildActivityLogResponse> listChildActivities(List<Long> childIds, int limit) {
        if (childIds == null || childIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> visibleChildIds = childIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (visibleChildIds.isEmpty()) {
            return Collections.emptyList();
        }
        int resolvedLimit = Math.max(MotivationConstants.Pagination.MIN_LIMIT,
                Math.min(limit, MotivationConstants.Pagination.ACTIVITY_LOG_MAX_LIMIT));
        List<MotivationSystemLog> logs = list(new LambdaQueryWrapper<MotivationSystemLog>()
                .in(MotivationSystemLog::getChildId, visibleChildIds)
                .eq(MotivationSystemLog::getSource, MotivationEnums.LogSource.BUSINESS.code())
                .orderByDesc(MotivationSystemLog::getUpdateTime)
                .orderByDesc(MotivationSystemLog::getCreateTime)
                .orderByDesc(MotivationSystemLog::getId)
                .last("limit " + resolvedLimit));
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> loggedChildIds = logs.stream()
                .map(MotivationSystemLog::getChildId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, MotivationChild> childMap = loggedChildIds.isEmpty()
                ? Collections.emptyMap()
                : childMapper.selectBatchIds(loggedChildIds).stream()
                .collect(Collectors.toMap(MotivationChild::getId, Function.identity(), (left, right) -> left));
        return logs.stream()
                .map((item) -> toActivityResponse(item, childMap.get(item.getChildId())))
                .toList();
    }

    private void saveLog(Long userId,
                         Long childId,
                         MotivationEnums.LogType logType,
                         String title,
                         String detail,
                         MotivationEnums.LogSource source,
                         LocalDateTime now) {
        MotivationSystemLog logEntity = new MotivationSystemLog();
        logEntity.setUserId(userId);
        logEntity.setChildId(childId);
        logEntity.setLogType(logType == null ? MotivationConstants.LogType.SYSTEM : logType.code());
        logEntity.setTitle(trimOrDefault(title, "系统记录"));
        logEntity.setDetail(trimToNull(detail));
        logEntity.setSource(source == null ? MotivationConstants.LogSource.SYSTEM : source.code());
        logEntity.setCreateTime(now);
        save(logEntity);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String businessDetail(String operatorName, LocalDateTime time, String action) {
        return "用户「" + operatorName + "」于 " + BUSINESS_TIME_FORMATTER.format(time) + " " + action;
    }

    private ChildActivityLogResponse toActivityResponse(MotivationSystemLog logEntity, MotivationChild child) {
        ChildActivityLogResponse response = new ChildActivityLogResponse();
        response.setId(logEntity.getId());
        response.setChildId(logEntity.getChildId());
        response.setChildNickname(child == null ? null : child.getNickname());
        response.setLogType(logEntity.getLogType());
        response.setTitle(logEntity.getTitle());
        response.setDetail(logEntity.getDetail());
        response.setCreateTime(logEntity.getCreateTime());
        return response;
    }

    private String resolveOperatorName(Long userId) {
        if (userId == null) {
            return DEFAULT_OPERATOR;
        }
        MotivationUser user = userMapper.selectOne(new LambdaQueryWrapper<MotivationUser>()
                .eq(MotivationUser::getId, userId)
                .last("limit 1"));
        if (user == null) {
            return DEFAULT_OPERATOR;
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : DEFAULT_OPERATOR;
    }
}
