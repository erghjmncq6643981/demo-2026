package com.chandler.motivation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationSystemLog;
import com.chandler.motivation.domain.dataobject.MotivationUser;
import com.chandler.motivation.domain.mapper.MotivationSystemLogMapper;
import com.chandler.motivation.domain.mapper.MotivationUserMapper;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
