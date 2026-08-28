package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.task.api.AiAsyncTaskAttemptResponse;
import com.chandler.learning.agent.task.api.AiAsyncTaskStepResponse;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.AiAsyncTaskAttempt;
import com.chandler.learning.agent.task.domain.AiAsyncTaskStep;
import com.chandler.learning.agent.task.domain.AiTaskStepStatus;
import com.chandler.learning.agent.task.infrastructure.AiAsyncTaskAttemptMapper;
import com.chandler.learning.agent.task.infrastructure.AiAsyncTaskStepMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 任务步骤、租约和执行尝试的通用应用服务。业务处理器通过该服务形成可恢复检查点。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskExecutionService {

    private final AiAsyncTaskStepMapper stepMapper;
    private final AiAsyncTaskAttemptMapper attemptMapper;
    private final ScheduledExecutorService leaseScheduler;

    /** 创建父任务时一次性写入稳定步骤，重试不会重复创建。 */
    @Transactional(rollbackFor = Exception.class)
    public void initialize(Long taskId, Long operatorUserId, List<AiTaskStepDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AiAsyncTaskStep> steps = new java.util.ArrayList<>(definitions.size());
        for (AiTaskStepDefinition definition : definitions) {
            AiAsyncTaskStep step = new AiAsyncTaskStep();
            step.setId(IdWorker.getId());
            step.setTaskId(taskId);
            step.setStepCode(definition.code());
            step.setStepName(definition.name());
            step.setStepOrder(definition.order());
            step.setStatus(AiTaskStepStatus.PENDING.getCode());
            step.setCompletedCount(LearningConstants.ZERO);
            step.setTotalCount(Math.max(1, definition.totalCount()));
            step.setAttemptCount(LearningConstants.ZERO);
            step.setMaxAttemptCount(LearningConstants.AiTask.DEFAULT_MAX_RETRY_COUNT + 1);
            step.setCreateBy(operatorUserId == null ? 0L : operatorUserId);
            step.setUpdateBy(operatorUserId == null ? 0L : operatorUserId);
            step.setCreateTime(now);
            step.setUpdateTime(now);
            step.setDeleted(false);
            step.setVersion(LearningConstants.ZERO);
            steps.add(step);
        }
        stepMapper.insertBatch(steps);
    }

    /** 执行一个必要步骤；已完成步骤直接复用，实现断点续跑。 */
    public <T> T execute(Long taskId, String stepCode, Long operatorUserId, Long modelConfigId,
                         Supplier<T> action) {
        AiAsyncTaskStep step = require(taskId, stepCode);
        if (AiTaskStepStatus.COMPLETED.getCode().equals(step.getStatus())) {
            return null;
        }
        String leaseToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusMinutes(LearningConstants.AiTask.STEP_LEASE_MINUTES);
        if (stepMapper.claim(step.getId(), leaseToken, now,
                leaseUntil) == LearningConstants.ZERO) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_GENERATION_IN_PROGRESS,
                    "任务步骤正在由其他执行器处理: " + step.getStepName());
        }
        int attemptNo = value(step.getAttemptCount()) + 1;
        AiAsyncTaskAttempt attempt = startAttempt(taskId, step.getId(), operatorUserId, modelConfigId, attemptNo, now);
        ScheduledFuture<?> heartbeat = startHeartbeat(step.getId(), leaseToken);
        try {
            T result = action.get();
            finishStep(step.getId(), leaseToken, AiTaskStepStatus.COMPLETED.getCode(), null, true);
            finishAttempt(attempt.getId(), AiTaskStepStatus.COMPLETED.getCode(), null);
            return result;
        } catch (RuntimeException ex) {
            finishStep(step.getId(), leaseToken, AiTaskStepStatus.FAILED.getCode(), ex.getMessage(), false);
            finishAttempt(attempt.getId(), AiTaskStepStatus.FAILED.getCode(), ex.getMessage());
            throw ex;
        } finally {
            heartbeat.cancel(false);
        }
    }

    private ScheduledFuture<?> startHeartbeat(Long stepId, String leaseToken) {
        long interval = Math.max(10L, LearningConstants.AiTask.STEP_HEARTBEAT_INTERVAL_SECONDS);
        return leaseScheduler.scheduleAtFixedRate(() -> {
            LocalDateTime heartbeatTime = LocalDateTime.now();
            try {
                int renewed = stepMapper.renew(stepId, leaseToken, heartbeatTime,
                        heartbeatTime.plusMinutes(LearningConstants.AiTask.STEP_LEASE_MINUTES));
                if (renewed == LearningConstants.ZERO) {
                    // 令牌失效时让当前动作尽快结束，调度器会负责恢复步骤。
                    log.warn("AI 任务步骤续租失败 stepId={}，租约可能已被回收", stepId);
                }
            } catch (RuntimeException ex) {
                log.debug("AI 任务步骤续租异常 stepId={}", stepId, ex);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    /** 将失败或中断步骤重新置为待执行，已成功步骤保持完成。 */
    public void resetRecoverableSteps(Long taskId, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        stepMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getTaskId, taskId)
                .in(AiAsyncTaskStep::getStatus, List.of(
                        AiTaskStepStatus.FAILED.getCode(), AiTaskStepStatus.CANCELLED.getCode()))
                .eq(AiAsyncTaskStep::getDeleted, false)
                .set(AiAsyncTaskStep::getStatus, AiTaskStepStatus.PENDING.getCode())
                .set(AiAsyncTaskStep::getLeaseToken, null)
                .set(AiAsyncTaskStep::getLeaseUntil, null)
                .set(AiAsyncTaskStep::getErrorMessage, null)
                .set(AiAsyncTaskStep::getFinishedTime, null)
                .set(AiAsyncTaskStep::getUpdateBy, operatorUserId)
                .set(AiAsyncTaskStep::getUpdateTime, now));
    }

    public void cancelPendingSteps(Long taskId, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        stepMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getTaskId, taskId)
                .in(AiAsyncTaskStep::getStatus, List.of(
                        AiTaskStepStatus.PENDING.getCode(), AiTaskStepStatus.RUNNING.getCode()))
                .eq(AiAsyncTaskStep::getDeleted, false)
                .set(AiAsyncTaskStep::getStatus, AiTaskStepStatus.CANCELLED.getCode())
                .set(AiAsyncTaskStep::getLeaseToken, null)
                .set(AiAsyncTaskStep::getLeaseUntil, null)
                .set(AiAsyncTaskStep::getFinishedTime, now)
                .set(AiAsyncTaskStep::getUpdateBy, operatorUserId)
                .set(AiAsyncTaskStep::getUpdateTime, now));
    }

    public void deleteStepsAndAttempts(Long taskId, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        stepMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getTaskId, taskId)
                .eq(AiAsyncTaskStep::getDeleted, false)
                .set(AiAsyncTaskStep::getDeleted, true)
                .set(AiAsyncTaskStep::getUpdateBy, operatorUserId)
                .set(AiAsyncTaskStep::getUpdateTime, now));

        attemptMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskAttempt>()
                .eq(AiAsyncTaskAttempt::getTaskId, taskId)
                .eq(AiAsyncTaskAttempt::getDeleted, false)
                .set(AiAsyncTaskAttempt::getDeleted, true)
                .set(AiAsyncTaskAttempt::getUpdateBy, operatorUserId)
                .set(AiAsyncTaskAttempt::getUpdateTime, now));
    }

    public int recoverExpired(LocalDateTime now) {
        return stepMapper.recoverExpired(now);
    }

    public List<AiAsyncTaskStepResponse> responses(Long taskId) {
        List<AiAsyncTaskStep> steps = stepMapper.selectList(new LambdaQueryWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getTaskId, taskId)
                .eq(AiAsyncTaskStep::getDeleted, false)
                .orderByAsc(AiAsyncTaskStep::getStepOrder));
        if (steps.isEmpty()) return List.of();
        Map<Long, List<AiAsyncTaskAttempt>> attempts = attemptMapper.selectList(
                        new LambdaQueryWrapper<AiAsyncTaskAttempt>()
                                .eq(AiAsyncTaskAttempt::getTaskId, taskId)
                                .eq(AiAsyncTaskAttempt::getDeleted, false)
                                .orderByAsc(AiAsyncTaskAttempt::getAttemptNo))
                .stream().collect(Collectors.groupingBy(AiAsyncTaskAttempt::getStepId));
        return steps.stream().map(step -> toResponse(step,
                attempts.getOrDefault(step.getId(), List.of()))).toList();
    }

    private AiAsyncTaskStep require(Long taskId, String stepCode) {
        AiAsyncTaskStep step = stepMapper.selectOne(new LambdaQueryWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getTaskId, taskId)
                .eq(AiAsyncTaskStep::getStepCode, stepCode)
                .eq(AiAsyncTaskStep::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (step == null) {
            throw LearningAssistantException.notFound(LearningConstants.ErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND);
        }
        return step;
    }

    private AiAsyncTaskAttempt startAttempt(Long taskId, Long stepId, Long operatorUserId,
                                            Long modelConfigId, int attemptNo, LocalDateTime now) {
        AiAsyncTaskAttempt attempt = new AiAsyncTaskAttempt();
        attempt.setTaskId(taskId);
        attempt.setStepId(stepId);
        attempt.setOperatorUserId(operatorUserId);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(AiTaskStepStatus.RUNNING.getCode());
        attempt.setModelConfigId(modelConfigId);
        attempt.setStartedTime(now);
        attempt.setCreateBy(operatorUserId == null ? 0L : operatorUserId);
        attempt.setUpdateBy(operatorUserId == null ? 0L : operatorUserId);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        attempt.setDeleted(false);
        attempt.setVersion(LearningConstants.ZERO);
        attemptMapper.insert(attempt);
        return attempt;
    }

    private void finishStep(Long stepId, String leaseToken, String status, String error, boolean completed) {
        LocalDateTime now = LocalDateTime.now();
        stepMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskStep>()
                .eq(AiAsyncTaskStep::getId, stepId)
                .eq(AiAsyncTaskStep::getLeaseToken, leaseToken)
                .eq(AiAsyncTaskStep::getDeleted, false)
                .set(AiAsyncTaskStep::getStatus, status)
                .set(AiAsyncTaskStep::getCompletedCount, completed ? 1 : 0)
                .set(AiAsyncTaskStep::getErrorMessage, limit(error))
                .set(AiAsyncTaskStep::getLeaseToken, null)
                .set(AiAsyncTaskStep::getLeaseUntil, null)
                .set(AiAsyncTaskStep::getHeartbeatTime, now)
                .set(AiAsyncTaskStep::getFinishedTime, now)
                .set(AiAsyncTaskStep::getUpdateTime, now));
    }

    private void finishAttempt(Long attemptId, String status, String error) {
        LocalDateTime now = LocalDateTime.now();
        attemptMapper.update(null, new LambdaUpdateWrapper<AiAsyncTaskAttempt>()
                .eq(AiAsyncTaskAttempt::getId, attemptId)
                .eq(AiAsyncTaskAttempt::getDeleted, false)
                .set(AiAsyncTaskAttempt::getStatus, status)
                .set(AiAsyncTaskAttempt::getErrorMessage, limit(error))
                .set(AiAsyncTaskAttempt::getFinishedTime, now)
                .set(AiAsyncTaskAttempt::getUpdateTime, now));
    }

    private AiAsyncTaskStepResponse toResponse(AiAsyncTaskStep step, List<AiAsyncTaskAttempt> attempts) {
        AiAsyncTaskStepResponse response = new AiAsyncTaskStepResponse();
        response.setId(step.getId());
        response.setStepCode(step.getStepCode());
        response.setStepName(step.getStepName());
        response.setStepOrder(step.getStepOrder());
        response.setStatus(step.getStatus());
        response.setCompletedCount(step.getCompletedCount());
        response.setTotalCount(step.getTotalCount());
        response.setAttemptCount(step.getAttemptCount());
        response.setMaxAttemptCount(step.getMaxAttemptCount());
        response.setErrorMessage(step.getErrorMessage());
        response.setHeartbeatTime(step.getHeartbeatTime());
        response.setStartedTime(step.getStartedTime());
        response.setFinishedTime(step.getFinishedTime());
        response.setAttempts(attempts.stream().map(this::toResponse).toList());
        return response;
    }

    private AiAsyncTaskAttemptResponse toResponse(AiAsyncTaskAttempt attempt) {
        AiAsyncTaskAttemptResponse response = new AiAsyncTaskAttemptResponse();
        response.setId(attempt.getId());
        response.setOperatorUserId(attempt.getOperatorUserId());
        response.setAttemptNo(attempt.getAttemptNo());
        response.setStatus(attempt.getStatus());
        response.setModelConfigId(attempt.getModelConfigId());
        response.setProvider(attempt.getProvider());
        response.setModelName(attempt.getModelName());
        response.setPromptTokens(attempt.getPromptTokens());
        response.setCompletionTokens(attempt.getCompletionTokens());
        response.setTotalTokens(attempt.getTotalTokens());
        response.setCostTime(attempt.getCostTime());
        response.setErrorCode(attempt.getErrorCode());
        response.setErrorMessage(attempt.getErrorMessage());
        response.setStartedTime(attempt.getStartedTime());
        response.setFinishedTime(attempt.getFinishedTime());
        return response;
    }

    private int value(Integer value) {
        return value == null ? LearningConstants.ZERO : value;
    }

    private String limit(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
