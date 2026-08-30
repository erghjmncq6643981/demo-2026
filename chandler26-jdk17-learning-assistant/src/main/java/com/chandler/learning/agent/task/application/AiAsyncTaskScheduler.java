package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 领取并分派到期异步任务。 */
    @Scheduled(fixedDelayString = "${learning.ai-task.poll-interval-ms:10000}")
    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverStaleTasks(now);
        promoteDueRetries(now);
        List<AiAsyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, AiTaskConstants.STATUS_PENDING)
                .eq(AiAsyncTask::getDeleted, false)
                .le(AiAsyncTask::getScheduledTime, now)
                .orderByDesc(AiAsyncTask::getPriority)
                .orderByAsc(AiAsyncTask::getCreateTime)
                .last("LIMIT 20"));
        for (AiAsyncTask task : tasks) {
            if (taskService.claim(task.getId())) {
                try {
                    dispatcher.dispatch(task);
                } catch (TaskRejectedException ex) {
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
