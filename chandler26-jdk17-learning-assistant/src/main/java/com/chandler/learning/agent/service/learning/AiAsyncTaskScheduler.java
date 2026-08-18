package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import com.chandler.learning.agent.mapper.learning.AiAsyncTaskMapper;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 低价时段和预约任务调度器。任务先入库，再由本组件定时原子领取。
 */
@Component
@RequiredArgsConstructor
public class AiAsyncTaskScheduler {

    private final AiAsyncTaskMapper taskMapper;
    private final AiAsyncTaskService taskService;
    private final AiAsyncTaskDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${learning.ai-task.poll-interval-ms:10000}")
    public void dispatchDueTasks() {
        List<AiAsyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AiAsyncTask>()
                .eq(AiAsyncTask::getStatus, LearningConstants.AiTask.STATUS_PENDING)
                .eq(AiAsyncTask::getDeleted, false)
                .le(AiAsyncTask::getScheduledTime, LocalDateTime.now())
                .orderByDesc(AiAsyncTask::getPriority)
                .orderByAsc(AiAsyncTask::getCreateTime)
                .last("LIMIT 20"));
        for (AiAsyncTask task : tasks) {
            if (taskService.claim(task.getId())) {
                dispatcher.dispatch(task);
            }
        }
    }
}
