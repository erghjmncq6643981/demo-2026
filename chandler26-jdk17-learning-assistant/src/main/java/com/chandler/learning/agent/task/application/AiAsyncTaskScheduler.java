package com.chandler.learning.agent.task.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chandler.learning.agent.task.domain.AiAsyncTask;
import com.chandler.learning.agent.task.infrastructure.AiAsyncTaskMapper;
import com.chandler.learning.agent.support.LearningConstants;
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

    @Scheduled(fixedDelayString = "${learning.ai-task.poll-interval-ms:10000}")
    public void dispatchDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverStaleTasks(now);
        List<AiAsyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
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
                            LearningConstants.AiTask.QUEUE_RETRY_DELAY_SECONDS));
                    log.info("AI 任务队列已满，任务稍后重试 taskId={} type={}",
                            task.getId(), task.getTaskType());
                    log.debug("AI 任务提交线程池失败 taskId={}", task.getId(), ex);
                }
            }
        }
    }

    /** 将失去心跳的运行中任务转为可人工重试的失败状态。 */
    void recoverStaleTasks(LocalDateTime now) {
        int recovered = taskMapper.update(null, new LambdaUpdateWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_RUNNING)
                .eq(AiAsyncTask::getDeleted, false)
                .lt(AiAsyncTask::getUpdateTime,
                        now.minusMinutes(LearningConstants.AiTask.RUNNING_TIMEOUT_MINUTES))
                .set(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_FAILED)
                .set(AiAsyncTask::getErrorMessage, LearningConstants.AiTask.RUNNING_TIMEOUT_MESSAGE)
                .set(AiAsyncTask::getFinishedTime, now)
                .set(AiAsyncTask::getUpdateTime, now));
        if (recovered > LearningConstants.ZERO) {
            log.info("回收超时 AI 异步任务，共 {} 个，状态已转为失败并等待人工重试", recovered);
        }
    }
}
