package com.chandler.learning.agent.task.application.contract;

import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;

import java.util.List;
import java.util.Map;

/**
 * 业务域实现的任务处理契约。通用任务域只负责调度，不依赖具体业务 Mapper。
 */
public interface AiTaskHandler {

    AiTaskType taskType();

    List<AiTaskStepDefinition> steps();

    void execute(AiAsyncTask task, Map<String, Object> payload);
}
