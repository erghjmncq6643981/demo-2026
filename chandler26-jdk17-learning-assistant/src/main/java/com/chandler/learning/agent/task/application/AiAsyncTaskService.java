package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskPageResponse;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskTriggerType;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final UserDisplayNameService userDisplayNameService;
    private final AiTaskExecutionService executionService;

    /** 创建待执行任务。低价时段未配置具体时间时默认安排到次日 00:00。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask create(Long userId, String taskType, String taskName, Long planId, Long unitId,
                              Long relatedJobId, String executionMode, LocalDateTime scheduledTime,
                              Integer priority, Integer totalCount, Map<String, Object> payload) {
        return create(userId, taskType, taskName, planId, unitId, relatedJobId, executionMode, scheduledTime,
                priority, totalCount, null, payload);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask create(Long userId, String taskType, String taskName, Long planId, Long unitId,
                              Long relatedJobId, String executionMode, LocalDateTime scheduledTime,
                              Integer priority, Integer totalCount, String idempotencyKey,
                              Map<String, Object> payload) {
        if (StringUtils.hasText(idempotencyKey)) {
            AiAsyncTask existing = findActiveByKey(userId, taskType, planId, idempotencyKey);
            if (existing != null) {
                log.info("event=ai_async_task_idempotent_hit taskId={} type={} idempotencyKey={}",
                        existing.getId(), taskType, idempotencyKey);
                return existing;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String mode = resolveExecutionMode(executionMode);
        AiTaskType.of(taskType);
        LocalDateTime executeAt = resolveScheduledTime(mode, scheduledTime, now);
        AiAsyncTask task = new AiAsyncTask();
        task.setUserId(userId);
        task.setOwnerUserId(userId);
        task.setTriggerUserId(userId);
        task.setOperatorUserId(userId);
        task.setTriggerType(AiTaskTriggerType.USER.getCode());
        task.setVisibility("owner_admin");
        task.setTaskType(taskType);
        task.setTaskName(taskName);
        task.setPlanId(planId);
        task.setUnitId(unitId);
        task.setRelatedJobId(relatedJobId);
        task.setIdempotencyKey(limitIdempotencyKey(idempotencyKey));
        task.setStatus(AiTaskConstants.STATUS_PENDING);
        task.setExecutionMode(mode);
        task.setScheduledTime(executeAt);
        task.setPriority(priority == null ? AiTaskConstants.DEFAULT_PRIORITY : priority);
        task.setTotalCount(totalCount == null ? CommonConstants.ZERO : totalCount);
        task.setSuccessCount(CommonConstants.ZERO);
        task.setFailedCount(CommonConstants.ZERO);
        task.setProgressPercent(CommonConstants.ZERO);
        task.setRetryCount(CommonConstants.ZERO);
        task.setMaxRetryCount(AiTaskConstants.DEFAULT_MAX_RETRY_COUNT);
        task.setPayloadJson(writeJson(payload));
        task.setCreateBy(userId);
        task.setUpdateBy(userId);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setDeleted(false);
        task.setVersion(CommonConstants.ZERO);
        taskMapper.insert(task);
        return task;
    }

    public AiAsyncTaskPageResponse page(Long userId, String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? AiTaskConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, AiTaskConstants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        Page<AiAsyncTask> taskPage = taskMapper.selectPage(new Page<>(current, size), wrapper);
        AiAsyncTaskPageResponse response = new AiAsyncTaskPageResponse();
        response.setItems(toResponses(taskPage.getRecords()));
        response.setTotal(taskPage.getTotal());
        response.setPage((int) taskPage.getCurrent());
        response.setPageSize((int) taskPage.getSize());
        return response;
    }

    /** 管理员分页查询所有用户的 AI 异步任务。 */
    public AiAsyncTaskPageResponse pageAll(String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? AiTaskConstants.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, AiTaskConstants.MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        Page<AiAsyncTask> taskPage = taskMapper.selectPage(new Page<>(current, size), wrapper);
        AiAsyncTaskPageResponse response = new AiAsyncTaskPageResponse();
        response.setItems(toResponses(taskPage.getRecords()));
        response.setTotal(taskPage.getTotal());
        response.setPage((int) taskPage.getCurrent());
        response.setPageSize((int) taskPage.getSize());
        return response;
    }

    public List<AiAsyncTaskResponse> list(Long userId, String status, Integer limit) {
        int resolvedLimit = limit == null ? AiTaskConstants.DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(limit, AiTaskConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last("LIMIT " + resolvedLimit);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        return toResponses(taskMapper.selectList(wrapper));
    }

    /** 管理员查询所有用户的 AI 异步任务。 */
    public List<AiAsyncTaskResponse> listAll(String status, Integer limit) {
        int resolvedLimit = limit == null ? AiTaskConstants.DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(limit, AiTaskConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last("LIMIT " + resolvedLimit);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        return toResponses(taskMapper.selectList(wrapper));
    }

    public AiAsyncTask require(Long userId, Long taskId) {
        AiAsyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (task == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 管理员详情或操作入口使用，不限制任务归属。 */
    public AiAsyncTask requireAny(Long taskId) {
        AiAsyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (task == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 同一计划同一日期只保留一个待执行或运行中的场景材料任务。 */
    public AiAsyncTask findActiveSceneMaterialTask(Long userId, Long planId) {
        return findActive(userId, AiTaskConstants.TYPE_SCENE_MATERIAL, planId, null);
    }

    public AiAsyncTask findActiveSceneMaterialTask(Long userId, Long planId, String idempotencyKey) {
        return findActiveByKey(userId, AiTaskConstants.TYPE_SCENE_MATERIAL, planId, idempotencyKey);
    }

    /** 查询同一业务资源的有效任务，防止重复提交和重复模型成本。 */
    public AiAsyncTask findActive(Long ownerUserId, String taskType, Long planId, Long unitId) {
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, ownerUserId)
                .eq(AiAsyncTask::getTaskType, taskType)
                .eq(AiAsyncTask::getPlanId, planId)
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_PENDING,
                        AiTaskConstants.STATUS_RUNNING,
                        AiTaskConstants.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last(CommonConstants.SQL_LIMIT_ONE);
        if (unitId != null) wrapper.eq(AiAsyncTask::getUnitId, unitId);
        return taskMapper.selectOne(wrapper);
    }

    /** 使用资源级幂等键查询活动任务，避免不同日期的任务相互阻塞。 */
    public AiAsyncTask findActiveByKey(Long ownerUserId, String taskType, Long planId, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return findActive(ownerUserId, taskType, planId, null);
        }
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, ownerUserId)
                .eq(AiAsyncTask::getTaskType, taskType)
                .eq(AiAsyncTask::getIdempotencyKey, limitIdempotencyKey(idempotencyKey))
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_PENDING,
                        AiTaskConstants.STATUS_RUNNING,
                        AiTaskConstants.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last(CommonConstants.SQL_LIMIT_ONE);
        if (planId == null) wrapper.isNull(AiAsyncTask::getPlanId);
        else wrapper.eq(AiAsyncTask::getPlanId, planId);
        return taskMapper.selectOne(wrapper);
    }

    /** 查询指定计划下所有处于活动状态的场景生成或重构任务对应的推荐日期。 */
    public Set<LocalDate> findActiveGeneratingDatesForPlan(Long ownerUserId, Long planId) {
        List<AiAsyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, ownerUserId)
                .eq(AiAsyncTask::getPlanId, planId)
                .in(AiAsyncTask::getTaskType, List.of(
                        AiTaskConstants.TYPE_SCENE_MATERIAL,
                        AiTaskConstants.TYPE_SCENE_MATERIAL_REGENERATION))
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_PENDING,
                        AiTaskConstants.STATUS_RUNNING,
                        AiTaskConstants.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false));
        Set<LocalDate> dates = new HashSet<>();
        for (AiAsyncTask task : tasks) {
            LocalDate d = extractTaskDate(task);
            if (d != null) dates.add(d);
        }
        return dates;
    }

    private LocalDate extractTaskDate(AiAsyncTask task) {
        if (task == null) return null;
        if (StringUtils.hasText(task.getPayloadJson())) {
            try {
                JsonNode node = objectMapper.readTree(task.getPayloadJson());
                String dateStr = node.path("recommendedDate").asText(null);
                if (StringUtils.hasText(dateStr)) {
                    return LocalDate.parse(dateStr.trim());
                }
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(task.getIdempotencyKey())) {
            String[] parts = task.getIdempotencyKey().split(":");
            if (parts.length >= 3) {
                try {
                    return LocalDate.parse(parts[parts.length - 1].trim());
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /** 原子领取任务，防止多实例或事件与调度器重复执行。 */
    public boolean claim(Long taskId) {
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .set(AiAsyncTask::getStartedTime, LocalDateTime.now())
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
        return updated > 0;
    }

    /** AI 线程池暂时无容量时释放领取状态，交给后续调度轮次重试。 */
    public void releaseClaim(Long taskId, LocalDateTime scheduledTime) {
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
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
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getTotalCount, resolvedTotal)
                .set(AiAsyncTask::getSuccessCount, Math.max(0, success))
                .set(AiAsyncTask::getFailedCount, Math.max(0, failed))
                .set(AiAsyncTask::getProgressPercent, progress)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
    }

    /** 将任务产出的业务记录绑定到任务，便于前端从任务详情跳转到结果。 */
    public void bindBusiness(Long taskId, String businessType, String businessId) {
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getBusinessType, businessType)
                .set(AiAsyncTask::getBusinessId, businessId)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
    }

    public void complete(Long taskId, String status, String errorMessage) {
        AiAsyncTask task = taskMapper.selectById(taskId);
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, status)
                .set(AiAsyncTask::getErrorMessage, limitError(errorMessage))
                .set(AiAsyncTask::getFinishedTime, LocalDateTime.now())
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now());
        if (AiTaskConstants.STATUS_COMPLETED.equals(status)) {
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
        if (List.of(AiTaskConstants.STATUS_COMPLETED,
                AiTaskConstants.STATUS_PARTIAL_FAILED,
                AiTaskConstants.STATUS_ATTENTION_REQUIRED,
                AiTaskConstants.STATUS_FAILED,
                AiTaskConstants.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_PENDING,
                        AiTaskConstants.STATUS_RUNNING))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_CANCELLED)
                .set(AiAsyncTask::getCancelledTime, now)
                .set(AiAsyncTask::getFinishedTime, now)
                .set(AiAsyncTask::getUpdateBy, userId)
                .set(AiAsyncTask::getUpdateTime, now));
        if (updated > 0) {
            executionService.cancelPendingSteps(taskId, userId);
            systemLogService.record(userId, SystemLogType.AI, "取消 AI 异步任务", task.getTaskName());
        }
        return require(userId, taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask retry(Long userId, Long taskId) {
        return retry(userId, taskId, null);
    }

    /** 重新排队失败或已取消的任务，并可替换本次 Worker 参数。手动重试将重置重试计数。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask retry(Long userId, Long taskId, Map<String, Object> payload) {
        AiAsyncTask task = require(userId, taskId);
        if (!List.of(AiTaskConstants.STATUS_FAILED,
                AiTaskConstants.STATUS_PARTIAL_FAILED,
                AiTaskConstants.STATUS_ATTENTION_REQUIRED,
                AiTaskConstants.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        int total = task.getTotalCount() == null ? 0 : Math.max(0, task.getTotalCount());
        int success = task.getSuccessCount() == null ? 0 : Math.max(0, task.getSuccessCount());
        int progress = total == 0 ? 0 : Math.min(100, success * 100 / total);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_FAILED,
                        AiTaskConstants.STATUS_PARTIAL_FAILED,
                        AiTaskConstants.STATUS_ATTENTION_REQUIRED,
                        AiTaskConstants.STATUS_CANCELLED))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getRetryCount, CommonConstants.ZERO)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .set(AiAsyncTask::getExecutionMode, AiTaskConstants.EXECUTION_IMMEDIATE)
                .set(AiAsyncTask::getFailedCount, CommonConstants.ZERO)
                .set(AiAsyncTask::getProgressPercent, progress)
                .set(AiAsyncTask::getErrorMessage, null)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getFinishedTime, null)
                .set(AiAsyncTask::getCancelledTime, null)
                .set(AiAsyncTask::getScheduledTime, now)
                .set(AiAsyncTask::getOperatorUserId, userId)
                .set(AiAsyncTask::getUpdateBy, userId)
                .set(AiAsyncTask::getUpdateTime, now);
        if (payload != null && !payload.isEmpty()) {
            wrapper.set(AiAsyncTask::getPayloadJson, writeJson(payload));
        }
        taskMapper.update(null, wrapper);
        executionService.resetRecoverableSteps(taskId, userId);
        return require(userId, taskId);
    }

    /** 将预约任务立即放入调度队列。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask runNow(Long userId, Long taskId) {
        AiAsyncTask task = require(userId, taskId);
        if (AiTaskConstants.STATUS_PENDING.equals(task.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                    .eq(AiAsyncTask::getId, taskId)
                    .eq(AiAsyncTask::getOwnerUserId, userId)
                    .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                    .eq(AiAsyncTask::getDeleted, false)
                    .set(AiAsyncTask::getExecutionMode, AiTaskConstants.EXECUTION_IMMEDIATE)
                    .set(AiAsyncTask::getScheduledTime, now)
                    .set(AiAsyncTask::getOperatorUserId, userId)
                    .set(AiAsyncTask::getUpdateBy, userId)
                    .set(AiAsyncTask::getUpdateTime, now));
        }
        return require(userId, taskId);
    }

    /** 管理员取消其他用户仍在排队或执行中的任务。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask cancelAsAdmin(Long operatorUserId, Long taskId) {
        AiAsyncTask task = requireAny(taskId);
        if (List.of(AiTaskConstants.STATUS_COMPLETED,
                AiTaskConstants.STATUS_PARTIAL_FAILED,
                AiTaskConstants.STATUS_ATTENTION_REQUIRED,
                AiTaskConstants.STATUS_FAILED,
                AiTaskConstants.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_PENDING,
                        AiTaskConstants.STATUS_RUNNING,
                        AiTaskConstants.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_CANCELLED)
                .set(AiAsyncTask::getCancelledTime, now)
                .set(AiAsyncTask::getFinishedTime, now)
                .set(AiAsyncTask::getOperatorUserId, operatorUserId)
                .set(AiAsyncTask::getUpdateBy, operatorUserId)
                .set(AiAsyncTask::getUpdateTime, now));
        executionService.cancelPendingSteps(taskId, operatorUserId);
        return requireAny(taskId);
    }

    /** 管理员代表任务归属人立即执行预约任务，并记录实际操作人。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask runNowAsAdmin(Long operatorUserId, Long taskId) {
        AiAsyncTask task = requireAny(taskId);
        AiAsyncTask updated = runNow(task.getOwnerUserId(), taskId);
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getOperatorUserId, operatorUserId)
                .set(AiAsyncTask::getUpdateBy, operatorUserId)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
        return requireAny(updated.getId());
    }

    /** 管理员代表任务归属人继续执行，保留成果归属并记录实际操作者。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask retryAsAdmin(Long operatorUserId, Long taskId) {
        AiAsyncTask task = requireAny(taskId);
        AiAsyncTask updated = retry(task.getOwnerUserId(), taskId);
        // retry 需要使用归属人做权限校验，但实际继续执行者必须保留管理员身份。
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getOperatorUserId, operatorUserId)
                .set(AiAsyncTask::getUpdateBy, operatorUserId)
                .set(AiAsyncTask::getUpdateTime, LocalDateTime.now()));
        return requireAny(updated.getId());
    }

    /** 用户删除自己的任务（软删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long taskId) {
        AiAsyncTask task = require(userId, taskId);
        deleteInternal(userId, task);
    }

    /** 管理员删除任意任务（软删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAsAdmin(Long operatorUserId, Long taskId) {
        AiAsyncTask task = requireAny(taskId);
        deleteInternal(operatorUserId, task);
    }

    private void deleteInternal(Long operatorUserId, AiAsyncTask task) {
        LocalDateTime now = LocalDateTime.now();
        if (List.of(AiTaskConstants.STATUS_PENDING,
                AiTaskConstants.STATUS_RUNNING,
                AiTaskConstants.STATUS_RETRY_WAIT).contains(task.getStatus())) {
            executionService.cancelPendingSteps(task.getId(), operatorUserId);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, task.getId())
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getDeleted, true)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_CANCELLED)
                .set(AiAsyncTask::getOperatorUserId, operatorUserId)
                .set(AiAsyncTask::getUpdateBy, operatorUserId)
                .set(AiAsyncTask::getUpdateTime, now));

        executionService.deleteStepsAndAttempts(task.getId(), operatorUserId);

        log.info("用户「{}」删除了 AI 异步任务 taskId={}「{}」",
                userDisplayNameService.userName(operatorUserId), task.getId(), task.getTaskName());
        systemLogService.record(operatorUserId, SystemLogType.AI, "删除 AI 异步任务",
                task.getTaskName() + " (taskId=" + task.getId() + ")");
    }

    public AiAsyncTaskResponse toResponse(AiAsyncTask task) {
        return toResponse(task, userDisplayNameService.userNames(List.of(
                task.getOwnerUserId(), task.getTriggerUserId(), task.getOperatorUserId())));
    }

    private List<AiAsyncTaskResponse> toResponses(Collection<AiAsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<AiAsyncTask> values = new ArrayList<>(tasks);
        List<Long> userIds = values.stream()
                .flatMap(task -> java.util.stream.Stream.of(task.getOwnerUserId(), task.getTriggerUserId(), task.getOperatorUserId()))
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = userDisplayNameService.userNames(userIds);
        return values.stream().map(task -> toResponse(task, names)).toList();
    }

    private AiAsyncTaskResponse toResponse(AiAsyncTask task, Map<Long, String> names) {
        AiAsyncTaskResponse response = new AiAsyncTaskResponse();
        response.setId(task.getId());
        response.setUserId(task.getUserId());
        response.setOwnerUserId(task.getOwnerUserId());
        response.setUserName(resolveName(names, task.getOwnerUserId()));
        response.setTriggerUserId(task.getTriggerUserId());
        response.setTriggerUserName(task.getTriggerUserId() == null
                ? "系统" : resolveName(names, task.getTriggerUserId()));
        response.setOperatorUserId(task.getOperatorUserId());
        response.setOperatorUserName(task.getOperatorUserId() == null
                ? null : resolveName(names, task.getOperatorUserId()));
        response.setTriggerType(task.getTriggerType());
        response.setVisibility(task.getVisibility());
        response.setTaskType(task.getTaskType());
        response.setTaskName(task.getTaskName());
        response.setPlanId(task.getPlanId());
        response.setUnitId(task.getUnitId());
        response.setRelatedJobId(task.getRelatedJobId());
        response.setBusinessType(task.getBusinessType());
        response.setBusinessId(task.getBusinessId());
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
        response.setSteps(List.of());
        return response;
    }

    private String resolveName(Map<Long, String> names, Long userId) {
        return names.getOrDefault(userId, "用户#" + userId);
    }

    /** 根据错误码决定自动重试还是转人工处理，并回收失败步骤作为断点。 */
    public void failFromException(Long taskId, RuntimeException exception) {
        AiAsyncTask task = taskMapper.selectById(taskId);
        if (task == null || !AiTaskConstants.STATUS_RUNNING.equals(task.getStatus())) return;
        String code = exception instanceof LearningAssistantException business
                ? business.getErrorCode() : null;
        boolean attention = ATTENTION_ERROR_CODES.contains(code);
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetryCount() == null
                ? AiTaskConstants.DEFAULT_MAX_RETRY_COUNT : task.getMaxRetryCount();
        boolean retryable = !attention && retryCount < maxRetry;
        LocalDateTime now = LocalDateTime.now();
        String message = limitError(exception.getMessage());
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, retryable
                        ? AiTaskConstants.STATUS_RETRY_WAIT
                        : (attention ? AiTaskConstants.STATUS_ATTENTION_REQUIRED
                        : AiTaskConstants.STATUS_FAILED))
                .set(AiAsyncTask::getErrorMessage, message)
                .set(AiAsyncTask::getFinishedTime, retryable ? null : now)
                .set(AiAsyncTask::getRetryCount, retryable ? retryCount + 1 : retryCount)
                .set(AiAsyncTask::getScheduledTime, retryable
                        ? now.plusSeconds(retryDelaySeconds(retryCount)) : now)
                .set(AiAsyncTask::getUpdateTime, now);
        if (taskMapper.update(null, wrapper) > 0 && retryable) {
            executionService.resetRecoverableSteps(taskId, task.getOperatorUserId());
        }
        log.info("AI 异步任务异常 taskId={} type={} errorCode={} status={} retryCount={}",
                taskId, task.getTaskType(), code, retryable ? AiTaskConstants.STATUS_RETRY_WAIT
                        : (attention ? AiTaskConstants.STATUS_ATTENTION_REQUIRED
                        : AiTaskConstants.STATUS_FAILED), retryable ? retryCount + 1 : retryCount);
    }

    /** 详情按需加载步骤和执行尝试，避免任务列表产生 N+1。 */
    public AiAsyncTaskResponse toDetailResponse(AiAsyncTask task) {
        AiAsyncTaskResponse response = toResponse(task);
        response.setSteps(executionService.responses(task.getId()));
        return response;
    }

    /** Worker 在批次边界检查取消状态，避免继续产生新的模型调用。 */
    public boolean isCancelled(Long taskId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_CANCELLED)
                .eq(AiAsyncTask::getDeleted, false)) > 0;
    }

    private String resolveExecutionMode(String executionMode) {
        String mode = StringUtils.hasText(executionMode)
                ? executionMode.trim() : AiTaskConstants.EXECUTION_IMMEDIATE;
        if (!List.of(AiTaskConstants.EXECUTION_IMMEDIATE,
                AiTaskConstants.EXECUTION_SCHEDULED,
                AiTaskConstants.EXECUTION_LOW_COST_WINDOW).contains(mode)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_ASYNC_TASK_EXECUTION_MODE_INVALID);
        }
        return mode;
    }

    private LocalDateTime resolveScheduledTime(String mode, LocalDateTime scheduledTime, LocalDateTime now) {
        if (AiTaskConstants.EXECUTION_SCHEDULED.equals(mode) && scheduledTime != null) {
            return scheduledTime.isBefore(now) ? now : scheduledTime;
        }
        if (AiTaskConstants.EXECUTION_LOW_COST_WINDOW.equals(mode)) {
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
            throw LearningAssistantException.badRequest(LearningErrorCode.JSON_PARSE_FAILED);
        }
    }

    private String limitError(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String limitIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
    }

    private long retryDelaySeconds(int retryCount) {
        return Math.min(AiTaskConstants.MAX_RETRY_DELAY_SECONDS,
                AiTaskConstants.RETRY_BASE_DELAY_SECONDS * (1L << Math.min(retryCount, 6)));
    }

    private static final Set<String> ATTENTION_ERROR_CODES = Set.of(
            LearningErrorCode.MODEL_CONFIG_NOT_FOUND.getCode(),
            LearningErrorCode.MODEL_CONFIG_NOT_BOUND.getCode(),
            LearningErrorCode.AI_PROVIDER_MISSING.getCode(),
            LearningErrorCode.AI_PROVIDER_DISABLED.getCode(),
            LearningErrorCode.AI_PROVIDER_API_KEY_MISSING.getCode(),
            LearningErrorCode.AI_PROVIDER_BASE_URL_MISSING.getCode(),
            LearningErrorCode.AI_MODEL_NAME_MISSING.getCode(),
            LearningErrorCode.AI_MODEL_UNSUPPORTED.getCode(),
            LearningErrorCode.AI_MODEL_BALANCE_INSUFFICIENT.getCode(),
            LearningErrorCode.AI_PROMPT_TOO_LARGE.getCode(),
            LearningErrorCode.AI_RESPONSE_PARSE_FAILED.getCode(),
            LearningErrorCode.AI_ASYNC_TASK_TYPE_INVALID.getCode(),
            LearningErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND.getCode(),
            LearningErrorCode.JSON_PARSE_FAILED.getCode());
}
