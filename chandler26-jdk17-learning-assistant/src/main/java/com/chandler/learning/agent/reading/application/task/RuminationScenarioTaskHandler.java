package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.application.RuminationGraphPersistenceService;
import com.chandler.learning.agent.reading.application.RuminationService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 知识反刍自定义场景星系构建异步任务处理器。
 * <p>拆分为两阶段：阶段 1 在事务外评估词本关联并调用默认大模型构建星系候选拓扑，
 * 阶段 2 在短事务内幂等落库；阶段 2 失败重试时直接复用检查点而不重调模型。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuminationScenarioTaskHandler implements AiTaskHandler {

    public static final String STEP_GENERATE = "generate_scenario";
    public static final String STEP_PERSIST = "persist_scenario";

    private final RuminationService ruminationService;
    private final RuminationGraphPersistenceService graphPersistenceService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回支持的任务类型枚举。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.RUMINATION_SCENARIO;
    }

    /** 定义自定义场景异步构建的两步可恢复执行顺序。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition(STEP_GENERATE, "筛选词本关联词汇并构建星系拓扑", 10),
                new AiTaskStepDefinition(STEP_PERSIST, "幂等落库自定义场景星系图谱", 20)
        );
    }

    /** 执行或断点续跑自定义场景构建任务。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long wordbookId = AiTaskPayload.longValue(payload, "wordbookId");
        String scenarioCode = stringValue(payload, "scenarioCode", "");
        String scenarioName = stringValue(payload, "scenarioName", "场景星网");
        String scenarioIcon = stringValue(payload, "scenarioIcon", "🏷️");
        String scenarioPrompt = stringValue(payload, "scenarioPrompt", scenarioName);
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        Long operator = task.getOperatorUserId();

        if (wordbookId == null || !StringUtils.hasText(scenarioCode) || !StringUtils.hasText(scenarioName)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "自定义场景异步任务快照参数缺失");
        }

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        RuminationScenarioCheckpoint stepOneResult = executionService.execute(
                task.getId(), STEP_GENERATE, operator, modelConfigId, () -> {
                    RuminationScenarioCheckpoint existing = executionService.getStepCheckpoint(
                            task.getId(), STEP_GENERATE, RuminationScenarioCheckpoint.class);
                    if (existing != null && existing.nodes() != null && !existing.nodes().isEmpty()) {
                        return existing;
                    }
                    RuminationScenarioCheckpoint generated = ruminationService.buildCustomScenarioCandidate(
                            task.getOwnerUserId(), wordbookId, scenarioCode,
                            scenarioName, scenarioIcon, scenarioPrompt);
                    executionService.saveStepCheckpoint(task.getId(), STEP_GENERATE, generated);
                    return generated;
                });
        taskService.updateProgress(task.getId(), 2, 1, 0);

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        Long persistedGraphId = executionService.execute(
                task.getId(), STEP_PERSIST, operator, null, () -> {
                    RuminationScenarioCheckpoint checkpoint = stepOneResult != null
                            ? stepOneResult
                            : executionService.getStepCheckpoint(
                                    task.getId(), STEP_GENERATE, RuminationScenarioCheckpoint.class);
                    if (checkpoint == null || checkpoint.nodes() == null || checkpoint.nodes().isEmpty()) {
                        throw LearningAssistantException.badRequest(
                                LearningErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND,
                                "未找到自定义场景生成检查点，请重新创建场景");
                    }
                    return graphPersistenceService.persistCustomScenarioForTask(
                            task.getOwnerUserId(), wordbookId, checkpoint);
                });

        if (persistedGraphId != null) {
            taskService.bindBusiness(task.getId(), AiTaskConstants.TYPE_RUMINATION_SCENARIO,
                    String.valueOf(persistedGraphId));
        }
        taskService.updateProgress(task.getId(), 2, 2, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }

    private String stringValue(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : defaultValue;
    }
}
