package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.learning.domain.constant.LearningActivityConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityDaily;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityEvent;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityDailyMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 将活动原始事件批量投影为日汇总读模型。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningActivityAggregationService {

    private final LearningActivityEventMapper eventMapper;
    private final LearningActivityDailyMapper dailyMapper;
    private final LearningActivityService activityService;

    /** 原子领取、汇总和完成标记必须处于一个短事务中。 */
    @Transactional(rollbackFor = Exception.class)
    public int processPendingBatch(int limit) {
        String claimToken = UUID.randomUUID().toString().replace("-", "");
        if (eventMapper.claimPendingBatch(claimToken, Math.max(1, Math.min(limit, 500))) <= CommonConstants.ZERO) {
            return CommonConstants.ZERO;
        }
        List<LearningActivityEvent> events = eventMapper.selectByClaimToken(claimToken);
        if (events.isEmpty()) {
            return CommonConstants.ZERO;
        }
        Map<DailyMetricKey, LearningActivityDaily> aggregates = new LinkedHashMap<>();
        for (LearningActivityEvent event : events) {
            LearningActivityEventType type = LearningActivityEventType.of(event.getEventType());
            if (type == null || event.getOccurredAt() == null) {
                log.warn("event=learning_activity_projection result=ignored eventId={} type={}",
                        event.getId(), event.getEventType());
                continue;
            }
            LocalDate date = event.getOccurredAt().toLocalDate();
            DailyMetricKey key = new DailyMetricKey(event.getUserId(), date, type.getCode());
            LearningActivityDaily daily = aggregates.computeIfAbsent(key,
                    item -> createDaily(item, event));
            daily.setMetricCount(safeAdd(daily.getMetricCount(), value(event.getQuantity())));
            daily.setDurationSeconds(safeAdd(daily.getDurationSeconds(), value(event.getDurationSeconds())));
            if (type == LearningActivityEventType.WORD_REVIEWED) {
                daily.setTotalCount(safeAdd(daily.getTotalCount(), value(event.getQuantity())));
                if ("correct".equalsIgnoreCase(event.getResultCode())
                        || "remembered".equalsIgnoreCase(event.getResultCode())) {
                    daily.setCorrectCount(safeAdd(daily.getCorrectCount(), value(event.getQuantity())));
                }
            }
        }
        if (!aggregates.isEmpty()) {
            dailyMapper.upsertBatch(new ArrayList<>(aggregates.values()));
        }
        eventMapper.markSucceededByClaimToken(claimToken);
        // 只在汇总提交后失效缓存，避免并发查询把提交前的数据重新缓存。
        List<Long> userIds = events.stream().map(LearningActivityEvent::getUserId).distinct().toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 汇总事务提交后清理受影响用户的活动缓存。 */
                @Override
                public void afterCommit() {
                    userIds.forEach(activityService::invalidate);
                }
            });
        } else {
            userIds.forEach(activityService::invalidate);
        }
        return events.size();
    }

    /** 恢复进程中断后遗留的 processing 事件。 */
    @Transactional(rollbackFor = Exception.class)
    public int resetStaleProcessing(LocalDateTime cutoffTime) {
        return eventMapper.resetStaleProcessing(cutoffTime);
    }

    private LearningActivityDaily createDaily(DailyMetricKey key, LearningActivityEvent event) {
        LearningActivityDaily daily = new LearningActivityDaily();
        daily.setUserId(key.userId());
        daily.setActivityDate(key.activityDate());
        daily.setMetricType(key.metricType());
        daily.setMetricCount(0);
        daily.setDurationSeconds(0);
        daily.setCorrectCount(0);
        daily.setTotalCount(0);
        daily.setCreateBy(event.getUserId());
        daily.setUpdateBy(event.getUserId());
        daily.setCreateTime(event.getOccurredAt());
        daily.setUpdateTime(event.getOccurredAt());
        daily.setDeleted(false);
        daily.setVersion(0);
        return daily;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeAdd(Integer left, int right) {
        long result = (long) value(left) + right;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private record DailyMetricKey(Long userId, LocalDate activityDate, String metricType) {
    }
}
