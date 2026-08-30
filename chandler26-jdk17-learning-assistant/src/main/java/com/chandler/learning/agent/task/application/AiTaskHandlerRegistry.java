package com.chandler.learning.agent.task.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 将任务类型映射到业务域处理器。 */
@Component
public class AiTaskHandlerRegistry {

    private final Map<AiTaskType, AiTaskHandler> handlers;

    public AiTaskHandlerRegistry(List<AiTaskHandler> handlers) {
        Map<AiTaskType, AiTaskHandler> indexed = new EnumMap<>(AiTaskType.class);
        for (AiTaskHandler handler : handlers) {
            AiTaskHandler duplicate = indexed.put(handler.taskType(), handler);
            if (duplicate != null) {
                throw new IllegalStateException("重复的 AI 任务处理器: " + handler.taskType().getCode());
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    /** 查询并校验AI 异步任务数据是否存在及可访问。 */
    public AiTaskHandler require(String taskType) {
        AiTaskType type = AiTaskType.of(taskType);
        AiTaskHandler handler = handlers.get(type);
        if (handler == null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_ASYNC_TASK_TYPE_INVALID,
                    "AI 任务尚未配置处理器: " + taskType);
        }
        return handler;
    }
}
