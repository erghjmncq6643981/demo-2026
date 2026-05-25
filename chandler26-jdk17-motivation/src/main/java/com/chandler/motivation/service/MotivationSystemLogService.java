package com.chandler.motivation.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chandler.motivation.domain.dataobject.MotivationSystemLog;
import com.chandler.motivation.domain.mapper.MotivationSystemLogMapper;
import com.chandler.motivation.support.MotivationConstants;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class MotivationSystemLogService extends ServiceImpl<MotivationSystemLogMapper, MotivationSystemLog> {

    public void record(Long userId, Long childId, String logType, String title, String detail) {
        MotivationSystemLog logEntity = new MotivationSystemLog();
        logEntity.setUserId(userId);
        logEntity.setChildId(childId);
        logEntity.setLogType(trimOrDefault(logType, MotivationConstants.LogType.SYSTEM));
        logEntity.setTitle(trimOrDefault(title, "系统记录"));
        logEntity.setDetail(trimToNull(detail));
        logEntity.setSource(MotivationConstants.LogSource.SERVER);
        logEntity.setCreateTime(LocalDateTime.now());
        save(logEntity);
        log.debug("业务日志已入库 userId={} childId={} type={} title={}",
                userId, childId, logEntity.getLogType(), logEntity.getTitle());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
