package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.task.api.AiAsyncTaskPageResponse;
import com.chandler.learning.agent.task.api.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.domain.AiAsyncTask;
import com.chandler.learning.agent.task.domain.AiTaskTriggerType;
import com.chandler.learning.agent.task.domain.AiTaskType;
import com.chandler.learning.agent.system.domain.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.infrastructure.AiAsyncTaskMapper;
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

    /** 创建带业务幂等键的任务；幂等键只约束同一业务资源的活动任务。 */
    @Transactional(rollbackFor = Exception.class)
    public AiAsyncTask create(Long userId, String taskType, String taskName, Long planId, Long unitId,
                              Long relatedJobId, String executionMode, LocalDateTime scheduledTime,
                              Integer priority, Integer totalCount, String idempotencyKey,
                              Map<String, Object> payload) {
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

    public AiAsyncTaskPageResponse page(Long userId, String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? LearningConstants.AiTask.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, LearningConstants.AiTask.MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        Page<AiAsyncTask> taskPage = taskMapper.selectPage(new Page<>(current, size), wrapper);
        AiAsyncTaskPageResponse response = new AiAsyncTaskPageResponse();
        response.setItems(taskPage.getRecords().stream().map(this::toResponse).toList());
        response.setTotal(taskPage.getTotal());
        response.setPage((int) taskPage.getCurrent());
        response.setPageSize((int) taskPage.getSize());
        return response;
    }

    /** 管理员分页查询所有用户的 AI 异步任务。 */
    public AiAsyncTaskPageResponse pageAll(String status, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? LearningConstants.AiTask.DEFAULT_PAGE_SIZE
                : Math.min(pageSize, LearningConstants.AiTask.MAX_PAGE_SIZE);
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        Page<AiAsyncTask> taskPage = taskMapper.selectPage(new Page<>(current, size), wrapper);
        AiAsyncTaskPageResponse response = new AiAsyncTaskPageResponse();
        response.setItems(taskPage.getRecords().stream().map(this::toResponse).toList());
        response.setTotal(taskPage.getTotal());
        response.setPage((int) taskPage.getCurrent());
        response.setPageSize((int) taskPage.getSize());
        return response;
    }

    public List<AiAsyncTaskResponse> list(Long userId, String status, Integer limit) {
        int resolvedLimit = limit == null ? LearningConstants.AiTask.DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(limit, LearningConstants.AiTask.MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last("LIMIT " + resolvedLimit);
        if (StringUtils.hasText(status)) {
            wrapper.eq(AiAsyncTask::getStatus, status.trim());
        }
        return taskMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    /** 管理员查询所有用户的 AI 异步任务。 */
    public List<AiAsyncTaskResponse> listAll(String status, Integer limit) {
        int resolvedLimit = limit == null ? LearningConstants.AiTask.DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(limit, LearningConstants.AiTask.MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
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
                .eq(AiAsyncTask::getOwnerUserId, userId)
                .eq(AiAsyncTask::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (task == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 管理员详情或操作入口使用，不限制任务归属。 */
    public AiAsyncTask requireAny(Long taskId) {
        AiAsyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (task == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.AI_ASYNC_TASK_NOT_FOUND);
        }
        return task;
    }

    /** 同一计划同一日期只保留一个待执行或运行中的场景材料任务。 */
    public AiAsyncTask findActiveSceneMaterialTask(Long userId, Long planId) {
        return findActive(userId, LearningConstants.AiTask.TYPE_SCENE_MATERIAL, planId, null);
    }

    public AiAsyncTask findActiveSceneMaterialTask(Long userId, Long planId, String idempotencyKey) {
        return findActiveByKey(userId, LearningConstants.AiTask.TYPE_SCENE_MATERIAL, planId, idempotencyKey);
    }

    /** 查询同一业务资源的有效任务，防止重复提交和重复模型成本。 */
    public AiAsyncTask findActive(Long ownerUserId, String taskType, Long planId, Long unitId) {
        LambdaQueryWrapper<AiAsyncTask> wrapper = new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getOwnerUserId, ownerUserId)
                .eq(AiAsyncTask::getTaskType, taskType)
                .eq(AiAsyncTask::getPlanId, planId)
                .in(AiAsyncTask::getStatus, List.of(
                        LearningConstants.AiTask.STATUS_PENDING,
                        LearningConstants.AiTask.STATUS_RUNNING,
                        LearningConstants.AiTask.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last(LearningConstants.SQL_LIMIT_ONE);
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
                        LearningConstants.AiTask.STATUS_PENDING,
                        LearningConstants.AiTask.STATUS_RUNNING,
                        LearningConstants.AiTask.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .orderByDesc(AiAsyncTask::getCreateTime)
                .last(LearningConstants.SQL_LIMIT_ONE);
        if (planId == null) wrapper.isNull(AiAsyncTask::getPlanId);
        else wrapper.eq(AiAsyncTask::getPlanId, planId);
        return taskMapper.selectOne(wrapper);
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
                LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED,
                LearningConstants.AiTask.STATUS_FAILED,
                LearningConstants.AiTask.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getOwnerUserId, userId)
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
        if (!List.of(LearningConstants.AiTask.STATUS_FAILED,
                LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED,
                LearningConstants.AiTask.STATUS_CANCELLED).contains(task.getStatus())) {
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
                        LearningConstants.AiTask.STATUS_FAILED,
                        LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                        LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED,
                        LearningConstants.AiTask.STATUS_CANCELLED))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getRetryCount, LearningConstants.ZERO)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                .set(AiAsyncTask::getExecutionMode, LearningConstants.AiTask.EXECUTION_IMMEDIATE)
                .set(AiAsyncTask::getFailedCount, LearningConstants.ZERO)
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
        if (LearningConstants.AiTask.STATUS_PENDING.equals(task.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                    .eq(AiAsyncTask::getId, taskId)
                    .eq(AiAsyncTask::getOwnerUserId, userId)
                    .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                    .eq(AiAsyncTask::getDeleted, false)
                    .set(AiAsyncTask::getExecutionMode, LearningConstants.AiTask.EXECUTION_IMMEDIATE)
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
        if (List.of(LearningConstants.AiTask.STATUS_COMPLETED,
                LearningConstants.AiTask.STATUS_PARTIAL_FAILED,
                LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED,
                LearningConstants.AiTask.STATUS_FAILED,
                LearningConstants.AiTask.STATUS_CANCELLED).contains(task.getStatus())) {
            return task;
        }
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .in(AiAsyncTask::getStatus, List.of(
                        LearningConstants.AiTask.STATUS_PENDING,
                        LearningConstants.AiTask.STATUS_RUNNING,
                        LearningConstants.AiTask.STATUS_RETRY_WAIT))
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_CANCELLED)
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
        if (List.of(LearningConstants.AiTask.STATUS_PENDING,
                LearningConstants.AiTask.STATUS_RUNNING,
                LearningConstants.AiTask.STATUS_RETRY_WAIT).contains(task.getStatus())) {
            executionService.cancelPendingSteps(task.getId(), operatorUserId);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, task.getId())
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getDeleted, true)
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_CANCELLED)
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
        AiAsyncTaskResponse response = new AiAsyncTaskResponse();
        response.setId(task.getId());
        response.setUserId(task.getUserId());
        response.setOwnerUserId(task.getOwnerUserId());
        response.setUserName(userDisplayNameService.userName(task.getOwnerUserId()));
        response.setTriggerUserId(task.getTriggerUserId());
        response.setTriggerUserName(task.getTriggerUserId() == null
                ? "系统" : userDisplayNameService.userName(task.getTriggerUserId()));
        response.setOperatorUserId(task.getOperatorUserId());
        response.setOperatorUserName(task.getOperatorUserId() == null
                ? null : userDisplayNameService.userName(task.getOperatorUserId()));
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

    /** 根据错误码决定自动重试还是转人工处理，并回收失败步骤作为断点。 */
    public void failFromException(Long taskId, RuntimeException exception) {
        AiAsyncTask task = taskMapper.selectById(taskId);
        if (task == null || !LearningConstants.AiTask.STATUS_RUNNING.equals(task.getStatus())) return;
        String code = exception instanceof LearningAssistantException business
                ? business.getErrorCode() : null;
        boolean attention = ATTENTION_ERROR_CODES.contains(code);
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetryCount() == null
                ? LearningConstants.AiTask.DEFAULT_MAX_RETRY_COUNT : task.getMaxRetryCount();
        boolean retryable = !attention && retryCount < maxRetry;
        LocalDateTime now = LocalDateTime.now();
        String message = limitError(exception.getMessage());
        LambdaUpdateWrapper<AiAsyncTask> wrapper = new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getId, taskId)
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, retryable
                        ? LearningConstants.AiTask.STATUS_RETRY_WAIT
                        : (attention ? LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED
                        : LearningConstants.AiTask.STATUS_FAILED))
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
                taskId, task.getTaskType(), code, retryable ? LearningConstants.AiTask.STATUS_RETRY_WAIT
                        : (attention ? LearningConstants.AiTask.STATUS_ATTENTION_REQUIRED
                        : LearningConstants.AiTask.STATUS_FAILED), retryable ? retryCount + 1 : retryCount);
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

    private String limitIdempotencyKey(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 160);
    }

    private long retryDelaySeconds(int retryCount) {
        return Math.min(LearningConstants.AiTask.MAX_RETRY_DELAY_SECONDS,
                LearningConstants.AiTask.RETRY_BASE_DELAY_SECONDS * (1L << Math.min(retryCount, 6)));
    }

    private static final Set<String> ATTENTION_ERROR_CODES = Set.of(
            LearningConstants.ErrorCode.MODEL_CONFIG_NOT_FOUND.getCode(),
            LearningConstants.ErrorCode.MODEL_CONFIG_NOT_BOUND.getCode(),
            LearningConstants.ErrorCode.AI_PROVIDER_MISSING.getCode(),
            LearningConstants.ErrorCode.AI_PROVIDER_DISABLED.getCode(),
            LearningConstants.ErrorCode.AI_PROVIDER_API_KEY_MISSING.getCode(),
            LearningConstants.ErrorCode.AI_PROVIDER_BASE_URL_MISSING.getCode(),
            LearningConstants.ErrorCode.AI_MODEL_NAME_MISSING.getCode(),
            LearningConstants.ErrorCode.AI_MODEL_UNSUPPORTED.getCode(),
            LearningConstants.ErrorCode.AI_MODEL_BALANCE_INSUFFICIENT.getCode(),
            LearningConstants.ErrorCode.AI_PROMPT_TOO_LARGE.getCode(),
            LearningConstants.ErrorCode.AI_RESPONSE_PARSE_FAILED.getCode(),
            LearningConstants.ErrorCode.AI_ASYNC_TASK_TYPE_INVALID.getCode(),
            LearningConstants.ErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND.getCode(),
            LearningConstants.ErrorCode.JSON_PARSE_FAILED.getCode());
}
