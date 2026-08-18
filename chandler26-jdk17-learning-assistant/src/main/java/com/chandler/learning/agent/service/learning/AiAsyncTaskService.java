package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.domain.dto.learning.AiAsyncTaskResponse;
import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.AiAsyncTaskMapper;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 异步任务统一生命周期服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiAsyncTaskService {

    private final AiAsyncTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final SystemLogService systemLogService;

    /** 创建待执行任务。低价时段未配置具体时间时默认安排到次日 00:00。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask create(Long userId, String taskType, String taskName, Long planId, Long unitId,
                              Long relatedJobId, String executionMode, LocalDateTime scheduledTime,
                              Integer priority, Integer totalCount, Map<String, Object> payload) {
        LocalDateTime now = LocalDateTime.now();
        String mode = resolveExecutionMode(executionMode);
        LocalDateTime executeAt = resolveScheduledTime(mode, scheduledTime, now);
        AiAsyncTask task = new AiAsyncTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setTaskName(taskName);
        task.setPlanId(planId);
        task.setUnitId(unitId);
        task.setRelatedJobId(relatedJobId);
        task.setStatus(LearningConstants.AiTask.STATUS_PENDING);
        task.setExecutionMode(mode);
        task.setScheduledTime(executeAt);
        task.setPriority(priority == null ? LearningConstants.AiTask.DEFAULT_PRIORITY : priority);
        task.setTotalCount(totalCount == null ? LearningConstants.ZERO : totalCount);
        task.setSuccessCount(LearningConstants.ZERO);
        task.setFailedCount(LearningConstants.ZERO);
        task.setProgressPercent(LearningConstants.ZERO);
        task.setRetryCount(LearningConstants.ZERO);
        task.setMaxRetryCount(LearningConstants.AiTask.DEFAULT_MAX_RETRY_COUNT);
        task.setPayloadJson(writeJson(payload));
        task.setCreateBy(userId);
        task.setUpdateBy(userId);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setDeleted(false);
        task.setVersion(LearningConstants.ZERO);
        taskMapper.insert(task);
        return task;
    }

    public List<AiAsyncTaskResponse> list(Long userId, String status, Integer limit) {
        int resolvedLimit = limit == null ? LearningConstants.AiTask.DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(limit, LearningConstants.AiTask.MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last("LIMIT " + resolvedLimit);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        return taskMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    public AiAsyncTask require(Long userId, Long taskId) {
        AiAsyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (task == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 同一计划只保留一个待执行或运行中的场景材料任务。 */
    public AiAsyncTask findActiveSceneMaterialTask(Long userId, Long planId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getUserId, userId)
                .eq(AiAsyncTask::getTaskType, LearningConstants.AiTask.TYPE_SCENE_MATERIAL)
                .eq(AiAsyncTask::getPlanId, planId)
                .in(AiAsyncTask::getStatus, List.of(
                        LearningConstants.AiTask.STATUS_PENDING,
                        LearningConstants.AiTask.STATUS_RUNNING))
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /** 原子领取任务，防止多实例或事件与调度器重复执行。 */
    public boolean claim(Long taskId) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .set(AiAsyncTask::getStartedTime, LocalDateTime.now())
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
        return updated > 0;
    }

    /** AI 线程池暂时无容量时释放领取状态，交给后续调度轮次重试。 */
    public void releaseClaim(Long taskId, LocalDateTime scheduledTime) {
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                .set(AiAsyncTask::getScheduledTime, scheduledTime)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
    }

    public void updateProgress(Long taskId, int total, int success, int failed) {
        int resolvedTotal = Math.max(0, total);
        int finished = Math.max(0, success) + Math.max(0, failed);
        int progress = resolvedTotal == 0 ? 0 : Math.min(100, finished * 100 / resolvedTotal);
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getTotalCount, resolvedTotal)
                .set(AiAsyncTask::getSuccessCount, Math.max(0, success))
                .set(AiAsyncTask::getFailedCount, Math.max(0, failed))
                .set(AiAsyncTask::getProgressPercent, progress)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
    }

    public void complete(Long taskId, String status, String errorMessage) {
        AiAsyncTask task = taskMapper.selectById(taskId);
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, status)
                .set(AiAsyncTask::getErrorMessage, limitError(errorMessage))
                .set(AiAsyncTask::getFinishedTime, LocalDateTime.now())
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now());
        if (LearningConstants.AiTask.STATUS_COMPLETED.equals(status)) {
            wrapper.set(AiAsyncTask::getProgressPercent, 100);
        }
        int updated = taskMapper.update(null, wrapper);
        if (updated > 0 && task != null) {
            log.info("AI 异步任务结束 taskId={} userId={} type={} status={}",
                    taskId, task.getUserId(), task.getTaskType(), status);
            systemLogService.record(task.getUserId(), SystemLogType.AI, "AI 异步任务结束",
                    task.getTaskName() + "，状态：" + status);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask cancel(Long userId, Long taskId) {
        AiAsyncTask task = require(userId, taskId);
        if (List.of(LearningConstants.AiTask.STATUS_COMPLETED,
                LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                LearningConstants.AiTask.STATUS_FAILED,
                LearningConstants.AiTask.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getUserId, userId)
                .in(AiAsyncTask::getStatus, List.of(
                        LearningConstants.AiTask.STATUS_PENDING,
                        LearningConstants.AiTask.STATUS_RUNNING))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_CANCELLED)
                .set(AiAsyncTask::getCancelledTime, now)
                .set(AiAsyncTask::getFinishedTime, now)
                .set(AiAsyncTask::getUpdateBy, userId)
                .set(AiAsyncTask::getUpdateTime, now));
        if (updated > 0) {
            systemLogService.record(userId, SystemLogType.AI, "取消 AI 异步任务", task.getTaskName());
        }
        return require(userId, taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask retry(Long userId, Long taskId) {
        return retry(userId, taskId, null);
    }

    /** 重新排队失败或已取消的任务，并可替换本次 Worker 参数。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask retry(Long userId, Long taskId, Map<String, Object> payload) {
        AiAsyncTask task = require(userId, taskId);
        if (!List.of(LearningConstants.AiTask.STATUS_FAILED,
                LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                LearningConstants.AiTask.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int total = task.getTotalCount() == null ? 0 : Math.max(0, task.getTotalCount());
        int success = task.getSuccessCount() == null ? 0 : Math.max(0, task.getSuccessCount());
        int progress = total == 0 ? 0 : Math.min(100, success * 100 / total);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getUserId, userId)
                .in(AiAsyncTask::getStatus, List.of(
                        LearningConstants.AiTask.STATUS_FAILED,
                        LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                        LearningConstants.AiTask.STATUS_CANCELLED))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getRetryCount, retryCount + 1)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                .set(AiAsyncTask::getExecutionMode, LearningConstants.AiTask.EXECUTION_IMMEDIATE)
                .set(AiAsyncTask::getFailedCount, LearningConstants.ZERO)
                .set(AiAsyncTask::getProgressPercent, progress)
                .set(AiAsyncTask::getErrorMessage, null)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getFinishedTime, null)
                .set(AiAsyncTask::getCancelledTime, null)
                .set(AiAsyncTask::getScheduledTime, now)
                .set(AiAsyncTask::getUpdateBy, userId)
                .set(AiAsyncTask::getUpdateTime, now);
        if (payload != null && !payload.isEmpty()) {
            wrapper.set(AiAsyncTask::getPayloadJson, writeJson(payload));
        }
        taskMapper.update(null, wrapper);
        return require(userId, taskId);
    }

    /** 将预约任务立即放入调度队列。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask runNow(Long userId, Long taskId) {
        AiAsyncTask task = require(userId, taskId);
        if (LearningConstants.AiTask.STATUS_PENDING.equals(task.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                    .eq(AiAsyncTask::getId, taskId)
                    .eq(AiAsyncTask::getUserId, userId)
                    .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                    .eq(AiAsyncTask::getDeleted, false)
                    .set(AiAsyncTask::getExecutionMode, LearningConstants.AiTask.EXECUTION_IMMEDIATE)
                    .set(AiAsyncTask::getScheduledTime, now)
                    .set(AiAsyncTask::getUpdateBy, userId)
                    .set(AiAsyncTask::getUpdateTime, now));
        }
        return require(userId, taskId);
    }

    public AiAsyncTaskResponse toResponse(AiAsyncTask task) {
        AiAsyncTaskResponse response = new AiAsyncTaskResponse();
        response.setId(task.getId());
        response.setTaskType(task.getTaskType());
        response.setTaskName(task.getTaskName());
        response.setPlanId(task.getPlanId());
        response.setUnitId(task.getUnitId());
        response.setRelatedJobId(task.getRelatedJobId());
        response.setStatus(task.getStatus());
        response.setExecutionMode(task.getExecutionMode());
        response.setScheduledTime(task.getScheduledTime());
        response.setPriority(task.getPriority());
        response.setTotalCount(task.getTotalCount());
        response.setSuccessCount(task.getSuccessCount());
        response.setFailedCount(task.getFailedCount());
        response.setProgressPercent(task.getProgressPercent());
        response.setRetryCount(task.getRetryCount());
        response.setMaxRetryCount(task.getMaxRetryCount());
        response.setErrorMessage(task.getErrorMessage());
        response.setStartedTime(task.getStartedTime());
        response.setFinishedTime(task.getFinishedTime());
        response.setCancelledTime(task.getCancelledTime());
        response.setCreateTime(task.getCreateTime());
        response.setUpdateTime(task.getUpdateTime());
        return response;
    }

    /** Worker 在批次边界检查取消状态，避免继续产生新的模型调用。 */
    public boolean isCancelled(Long taskId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_CANCELLED)
                .eq(AiAsyncTask::getDeleted, false)) > 0;
    }

    private String resolveExecutionMode(String executionMode) {
        String mode = StringUtils.hasText(executionMode)
                ? executionMode.trim() : LearningConstants.AiTask.EXECUTION_IMMEDIATE;
        if (!List.of(LearningConstants.AiTask.EXECUTION_IMMEDIATE,
                LearningConstants.AiTask.EXECUTION_SCHEDULED,
                LearningConstants.AiTask.EXECUTION_LOW_COST_WINDOW).contains(mode)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.AI_ASYNC_TASK_EXECUTION_MODE_INVALID);
        }
        return mode;
    }

    private LocalDateTime resolveScheduledTime(String mode, LocalDateTime scheduledTime, LocalDateTime now) {
        if (LearningConstants.AiTask.EXECUTION_SCHEDULED.equals(mode) && scheduledTime != null) {
            return scheduledTime.isBefore(now) ? now : scheduledTime;
        }
        if (LearningConstants.AiTask.EXECUTION_LOW_COST_WINDOW.equals(mode)) {
            LocalDateTime lowCostStart = now.toLocalDate().atStartOfDay();
            return now.isBefore(lowCostStart.plusHours(6)) ? lowCostStart : lowCostStart.plusDays(1);
        }
        return now;
    }

    private String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw LearningAssistantException.badRequest(LearningConstants.ErrorCode.JSON_PARSE_FAILED);
        }
    }

    private String limitError(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
