package com.chandler.learning.agent.task.application;

import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将已领取的任务路由到具体业务 Worker。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAsyncTaskDispatcher {

    private final AiAsyncTaskService taskService;
    private final AiTaskHandlerRegistry handlerRegistry;
    private final AiTaskExecutionService executionService;
    private final ObjectMapper objectMapper;

    /** 分派异步任务到受控线程池。 */
    @Async("aiTaskExecutor")
    public void dispatch(AiAsyncTask task) {
        try {
            if (taskService.isCancelled(task.getId())) {
                return;
            }
            Map<String, Object> payload = readPayload(task.getPayloadJson());
            var handler = handlerRegistry.require(task.getTaskType());
            if (executionService.responses(task.getId()).isEmpty()) {
                executionService.initialize(task.getId(), task.getOperatorUserId(), handler.steps());
            }
            handler.execute(task, payload);
        } catch (AiAsyncTaskCancelledException ex) {
            log.info("用户取消 AI 异步任务，Worker 已停止 taskId={} type={} userId={}",
                    task.getId(), task.getTaskType(), task.getUserId());
        } catch (RuntimeException ex) {
            taskService.failFromException(task.getId(), ex);
            log.info("AI 异步任务执行失败 taskId={} type={} userId={}，等待状态机处理",
                    task.getId(), task.getTaskType(), task.getUserId());
            log.debug("AI 异步任务异常详情 taskId={}", task.getId(), ex);
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson == null ? "{}" : payloadJson,
                    new TypeReference<>() {
                    });
        } catch (Exception ex) {
            return Map.of();
        }
    }

}
