package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 低价时段和预约任务调度器。任务先入库，再由本组件定时原子领取。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiAsyncTaskScheduler {

    private final AiAsyncTaskMapper taskMapper;
    private final AiAsyncTaskService taskService;
    private final AiAsyncTaskDispatcher dispatcher;
    private final AiTaskExecutionService executionService;

    /** 应用启动就绪后，立即恢复所有在上次停机时中断的运行中任务与步骤，无需等待心跳超时。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        LocalDateTime now = LocalDateTime.now();
        int recoveredSteps = executionService.recoverAllRunning(now);
        int recoveredTasks = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .set(AiAsyncTask::getErrorMessage, "系统启动自动恢复：从上次中断步骤断点续跑")
                .set(AiAsyncTask::getScheduledTime, now)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getFinishedTime, null)
                .set(AiAsyncTask::getUpdateTime, now));
        if (recoveredTasks > CommonConstants.ZERO || recoveredSteps > CommonConstants.ZERO) {
            log.info("系统启动完成，已自动恢复中断任务 taskCount={} stepCount={}", recoveredTasks, recoveredSteps);
        }
        dispatchDueTasks();
    }

    /** 领取并分派到期异步任务。 */
    @Scheduled(fixedDelayString = "${learning.ai-task.poll-interval-ms:5000}")
    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverStaleTasks(now);
        promoteDueRetries(now);

        // 收集当前正在占用词表分配锁的学习计划 ID：
        // 仅当一个活动任务（RUNNING / RETRY_WAIT）的材料与核心词生成步骤尚未完成时，才视为独占 planId 词表锁。
        // 一旦材料生成步骤完成，词表分配锁已释放，后续日期的任务可以立即启动，与前序任务的相关词生成步骤流水线重叠执行！
        List<AiAsyncTask> activePlanTasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .in(AiAsyncTask::getStatus, List.of(
                        AiTaskConstants.STATUS_RUNNING,
                        AiTaskConstants.STATUS_RETRY_WAIT))
                .isNotNull(AiAsyncTask::getPlanId)
                .eq(AiAsyncTask::getDeleted, false));

        Set<Long> occupiedPlanIds = activePlanTasks.stream()
                .filter(task -> !executionService.isMaterialStepCompleted(task.getId(), task.getTaskType()))
                .map(AiAsyncTask::getPlanId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<AiAsyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .eq(AiAsyncTask::getDeleted, false)
                .le(AiAsyncTask::getScheduledTime, now)
                .orderByDesc(AiAsyncTask::getPriority)
                .orderByAsc(AiAsyncTask::getCreateTime)
                .last("LIMIT 20"));

        for (AiAsyncTask task : tasks) {
            Long planId = task.getPlanId();
            boolean requiresMaterialLock = AiTaskType.SCENE_MATERIAL.getCode().equals(task.getTaskType())
                    || AiTaskType.SCENE_MATERIAL_REGENERATION.getCode().equals(task.getTaskType());
            if (requiresMaterialLock && planId != null && occupiedPlanIds.contains(planId)) {
                // 同一学习计划已有前序场景材料任务正在分配词表，后序场景材料生成任务在队列中保持 PENDING 排队
                log.debug("学习计划已有正在生成材料的任务，跳过当前任务派发保持排队 taskId={} planId={}",
                        task.getId(), planId);
                continue;
            }

            if (taskService.claim(task.getId())) {
                if (requiresMaterialLock && planId != null) {
                    occupiedPlanIds.add(planId);
                }
                try {
                    dispatcher.dispatch(task);
                } catch (TaskRejectedException ex) {
                    if (requiresMaterialLock && planId != null) {
                        occupiedPlanIds.remove(planId);
                    }
                    taskService.releaseClaim(task.getId(), now.plusSeconds(
                            AiTaskConstants.QUEUE_RETRY_DELAY_SECONDS));
                    log.info("AI 任务队列已满，任务稍后重试 taskId={} type={}",
                            task.getId(), task.getTaskType());
                    log.debug("AI 任务提交线程池失败 taskId={}", task.getId(), ex);
                }
            }
        }
    }

    /** 到达退避时间后重新放入待领取队列，失败步骤会在 Worker 中断点续跑。 */
    private void promoteDueRetries(LocalDateTime now) {
        int promoted = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RETRY_WAIT)
                .eq(AiAsyncTask::getDeleted, false)
                .le(AiAsyncTask::getScheduledTime, now)
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getFinishedTime, null)
                .set(AiAsyncTask::getUpdateTime, now));
        if (promoted > 0) log.info("AI 异步任务退避结束，重新排队 count={}", promoted);
    }

    /** 将失去心跳的运行中任务转为可人工重试的失败状态。 */
    void recoverStaleTasks(LocalDateTime now) {
        int recoveredSteps = executionService.recoverExpired(now);
        int recovered = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .lt(AiAsyncTask::getUpdateTime,
                        now.minusMinutes(AiTaskConstants.RUNNING_TIMEOUT_MINUTES))
                .set(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .set(AiAsyncTask::getErrorMessage, "执行进程中断，系统将从未完成步骤继续")
                .set(AiAsyncTask::getScheduledTime, now)
                .set(AiAsyncTask::getStartedTime, null)
                .set(AiAsyncTask::getFinishedTime, null)
                .set(AiAsyncTask::getUpdateTime, now));
        if (recovered > CommonConstants.ZERO || recoveredSteps > CommonConstants.ZERO) {
            log.info("回收中断 AI 异步任务 taskCount={} stepCount={}，将从未完成步骤自动继续", recovered, recoveredSteps);
        }
    }
}
