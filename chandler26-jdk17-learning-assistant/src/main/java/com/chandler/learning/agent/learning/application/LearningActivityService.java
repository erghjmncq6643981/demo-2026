package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.learning.api.response.LearningActivityDayResponse;
import com.chandler.learning.agent.learning.api.response.LearningActivityResponse;
import com.chandler.learning.agent.learning.domain.bo.LearningActivityMetricBO;
import com.chandler.learning.agent.learning.domain.constant.LearningActivityConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningActivityEvent;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityDailyMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningActivityEventMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 学习活动应用服务。
 * <p>
 * 业务动作只写入轻量待投影事件，后台投影器负责更新日汇总；活动查询只读取日汇总读模型。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningActivityService {

    private final LearningActivityEventMapper eventMapper;
    private final LearningActivityDailyMapper dailyMapper;

    /** 活动统计是读多写少的派生数据，短 TTL 避免重复读取年度汇总。 */
    private final Cache<String, LearningActivityResponse> activityCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();
    private final ConcurrentHashMap<String, CompletableFuture<LearningActivityResponse>> activityRequests =
            new ConcurrentHashMap<>();
    /** 当前进程内的用户活动版本，避免旧查询结果覆盖新活动。 */
    private final ConcurrentHashMap<Long, Long> activityVersions = new ConcurrentHashMap<>();

    @Qualifier("readQueryExecutor")
    private final Executor readQueryExecutor;

    /**
     * 记录一条待投影学习活动。调用方通常处于核心业务事务内，单条插入不会等待汇总任务。
     */
    public void record(Long userId, LearningActivityEventType type, LocalDateTime occurredAt,
                       Long planId, Long unitId, Long materialId, Long entryId,
                       Integer quantity, Integer durationSeconds, String resultCode,
                       String idempotencyKey) {
        if (userId == null || type == null) {
            return;
        }
        String resolvedKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : type.getCode() + ":" + UUID.randomUUID();
        LocalDateTime resolvedTime = occurredAt == null ? LocalDateTime.now() : occurredAt;
        LearningActivityEvent event = LearningActivityEvent.create(
                userId, type.getCode(), resolvedTime, planId, unitId, materialId, entryId,
                quantity, durationSeconds, resultCode, resolvedKey);
        try {
            if (eventMapper.insertIgnore(event) > 0) {
                invalidate(userId);
            }
        } catch (RuntimeException ex) {
            // 活动统计是派生数据，表结构未升级或写入暂时失败时不能阻断核心学习动作。
            log.warn("event=learning_activity_record result=failed userId={} type={} error={}",
                    userId, type.getCode(), ex.getClass().getSimpleName());
            log.debug("学习活动事件写入失败 userId={} type={}", userId, type.getCode(), ex);
        }
    }

    /** 查询用户活动统计；数据源为日汇总表而不是业务明细表。 */
    public LearningActivityResponse activity(Long userId, int days) {
        int resolvedDays = resolveDays(days);
        long version = activityVersion(userId);
        String cacheKey = cacheKey(userId, resolvedDays, version);
        LearningActivityResponse cached = activityCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        LearningActivityResponse response = loadActivity(userId, resolvedDays);
        if (activityVersion(userId) == version) {
            activityCache.put(cacheKey, response);
        }
        return response;
    }

    /** 异步查询年度活动统计，并合并相同用户和统计范围的并发请求。 */
    public CompletableFuture<LearningActivityResponse> activityAsync(Long userId, int days) {
        int resolvedDays = resolveDays(days);
        long version = activityVersion(userId);
        String key = cacheKey(userId, resolvedDays, version);
        LearningActivityResponse cached = activityCache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<LearningActivityResponse> pending = new CompletableFuture<>();
        CompletableFuture<LearningActivityResponse> existing = activityRequests.putIfAbsent(key, pending);
        if (existing != null) {
            return existing;
        }
        // 先登记再调度，避免任务立即完成时递归修改 computeIfAbsent 的键。
        try {
            readQueryExecutor.execute(() -> {
                try {
                    LearningActivityResponse response = loadActivity(userId, resolvedDays);
                    if (activityVersion(userId) == version) {
                        activityCache.put(key, response);
                    }
                    pending.complete(response);
                } catch (RuntimeException ex) {
                    pending.completeExceptionally(ex);
                } finally {
                    activityRequests.remove(key, pending);
                }
            });
        } catch (RuntimeException ex) {
            activityRequests.remove(key, pending);
            pending.completeExceptionally(ex);
        }
        return pending;
    }

    /** 活动事件新增或复习提交后失效用户派生统计缓存。 */
    public void invalidate(Long userId) {
        if (userId == null) {
            return;
        }
        activityVersions.merge(userId, 1L, Long::sum);
        String prefix = userId + ":";
        activityCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        activityRequests.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private int resolveDays(int days) {
        return Math.max(LearningActivityConstants.MIN_DAYS,
                Math.min(days, LearningActivityConstants.MAX_DAYS));
    }

    private LearningActivityResponse loadActivity(Long userId, int resolvedDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(resolvedDays - 1L);
        Map<LocalDate, LearningActivityDayResponse> dayMap = new LinkedHashMap<>();
        for (int index = 0; index < resolvedDays; index++) {
            LocalDate date = startDate.plusDays(index);
            LearningActivityDayResponse item = new LearningActivityDayResponse();
            item.setDate(date.toString());
            item.setLearnedCount(0);
            item.setWordAddedCount(0);
            item.setReviewCount(0);
            item.setSceneCompletedCount(0);
            item.setArticleCompletedCount(0);
            item.setStudySeconds(0);
            item.setTotalCount(0);
            dayMap.put(date, item);
        }

        List<LearningActivityMetricBO> metrics = dailyMapper.selectMetrics(userId, startDate, endDate);
        for (LearningActivityMetricBO metric : metrics) {
            LearningActivityDayResponse item = dayMap.get(metric.getActivityDate());
            if (item == null) {
                continue;
            }
            int count = nullToZero(metric.getMetricCount());
            String type = metric.getMetricType();
            if (LearningActivityEventType.WORD_LEARNED.getCode().equals(type)) {
                item.setLearnedCount(item.getLearnedCount() + count);
            } else if (LearningActivityEventType.WORD_ADDED.getCode().equals(type)) {
                item.setWordAddedCount(item.getWordAddedCount() + count);
            } else if (LearningActivityEventType.WORD_REVIEWED.getCode().equals(type)) {
                item.setReviewCount(item.getReviewCount() + count);
            } else if (LearningActivityEventType.SCENE_COMPLETED.getCode().equals(type)) {
                item.setSceneCompletedCount(item.getSceneCompletedCount() + count);
            } else if (LearningActivityEventType.ARTICLE_COMPLETED.getCode().equals(type)) {
                item.setArticleCompletedCount(item.getArticleCompletedCount() + count);
            }
            item.setStudySeconds(item.getStudySeconds() + nullToZero(metric.getDurationSeconds()));
        }

        int learnedTotal = 0;
        int wordAddedTotal = 0;
        int reviewTotal = 0;
        int sceneCompletedTotal = 0;
        int articleCompletedTotal = 0;
        int studySecondsTotal = 0;
        for (LearningActivityDayResponse item : dayMap.values()) {
            int total = nullToZero(item.getLearnedCount())
                    + nullToZero(item.getWordAddedCount())
                    + nullToZero(item.getReviewCount())
                    + nullToZero(item.getSceneCompletedCount())
                    + nullToZero(item.getArticleCompletedCount());
            item.setTotalCount(total);
            learnedTotal += nullToZero(item.getLearnedCount());
            wordAddedTotal += nullToZero(item.getWordAddedCount());
            reviewTotal += nullToZero(item.getReviewCount());
            sceneCompletedTotal += nullToZero(item.getSceneCompletedCount());
            articleCompletedTotal += nullToZero(item.getArticleCompletedCount());
            studySecondsTotal += nullToZero(item.getStudySeconds());
        }

        LearningActivityResponse response = new LearningActivityResponse();
        response.setDays(resolvedDays);
        response.setLearnedTotal(learnedTotal);
        response.setWordAddedTotal(wordAddedTotal);
        response.setReviewTotal(reviewTotal);
        response.setSceneCompletedTotal(sceneCompletedTotal);
        response.setArticleCompletedTotal(articleCompletedTotal);
        response.setStudySecondsTotal(studySecondsTotal);
        response.setItems(List.copyOf(dayMap.values()));
        return response;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long activityVersion(Long userId) {
        return userId == null ? 0L : activityVersions.getOrDefault(userId, 0L);
    }

    private String cacheKey(Long userId, int days, long version) {
        return userId + ":" + days + ":" + LocalDate.now() + ":" + version;
    }
}
