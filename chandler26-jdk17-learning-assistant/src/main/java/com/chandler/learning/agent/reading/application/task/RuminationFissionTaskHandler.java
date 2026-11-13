package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
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
 * 知识反刍节点 4D 衍生裂变异步任务处理器。
 * <p>任务严格绑定提交时的原单词本、原场景编码与父节点标识；
 * 阶段 1 产出候选 4D 扩展检查点，阶段 2 落库前在行锁下重检父节点存在性，确保结果仅归属原场景。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuminationFissionTaskHandler implements AiTaskHandler {

    public static final String STEP_GENERATE = "generate_fission";
    public static final String STEP_PERSIST = "persist_fission";

    private final RuminationService ruminationService;
    private final RuminationGraphPersistenceService graphPersistenceService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回支持的任务类型枚举。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.RUMINATION_FISSION;
    }

    /** 定义节点衍生裂变的两步可恢复执行顺序。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition(STEP_GENERATE, "调用模型生成 4D 衍生关联节点", 10),
                new AiTaskStepDefinition(STEP_PERSIST, "复核原场景父节点并幂等落库扩展", 20)
        );
    }

    /** 执行或断点续跑节点 4D 衍生裂变任务。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long wordbookId = AiTaskPayload.longValue(payload, "wordbookId");
        String scenarioCode = stringValue(payload, "scenarioCode", "");
        String scenarioName = stringValue(payload, "scenarioName", "场景星网");
        String parentNodeId = stringValue(payload, "parentNodeId", "");
        String targetWord = stringValue(payload, "targetWord", "");
        String targetDefinition = stringValue(payload, "targetDefinition", "");
        List<String> existingTerms = extractStringList(payload, "existingTerms");
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        Long operator = task.getOperatorUserId();

        if (wordbookId == null || !StringUtils.hasText(scenarioCode)
                || !StringUtils.hasText(parentNodeId) || !StringUtils.hasText(targetWord)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "节点裂变异步任务绑定上下文参数不完整");
        }

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        RuminationFissionCheckpoint stepOneResult = executionService.execute(
                task.getId(), STEP_GENERATE, operator, modelConfigId, () -> {
                    RuminationFissionCheckpoint existing = executionService.getStepCheckpoint(
                            task.getId(), STEP_GENERATE, RuminationFissionCheckpoint.class);
                    if (existing != null && existing.candidateNodes() != null) {
                        return existing;
                    }
                    RuminationFissionCheckpoint generated = ruminationService.buildFissionCandidate(
                            task.getOwnerUserId(), wordbookId, scenarioCode, scenarioName,
                            parentNodeId, targetWord, targetDefinition, existingTerms);
                    executionService.saveStepCheckpoint(task.getId(), STEP_GENERATE, generated);
                    return generated;
                });
        taskService.updateProgress(task.getId(), 2, 1, 0);

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        executionService.execute(
                task.getId(), STEP_PERSIST, operator, null, () -> {
                    RuminationFissionCheckpoint checkpoint = stepOneResult != null
                            ? stepOneResult
                            : executionService.getStepCheckpoint(
                                    task.getId(), STEP_GENERATE, RuminationFissionCheckpoint.class);
                    if (checkpoint == null) {
                        throw LearningAssistantException.badRequest(
                                LearningErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND,
                                "未找到节点衍生裂变检查点，请重新发起裂变");
                    }
                    RuminationFissionResponse applied = graphPersistenceService.persistFissionForTask(
                            task.getOwnerUserId(),
                            checkpoint,
                            () -> "produce_kitchen".equals(scenarioCode)
                                    ? ruminationService.buildPresetGraphSnapshot(
                                            task.getOwnerUserId(), wordbookId, scenarioCode)
                                    : null);
                    executionService.saveStepCheckpoint(task.getId(), STEP_PERSIST, applied);
                    return applied;
                });

        taskService.bindBusiness(task.getId(), AiTaskConstants.TYPE_RUMINATION_FISSION, scenarioCode);
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

    private List<String> extractStringList(Map<String, Object> payload, String key) {
        Object raw = payload == null ? null : payload.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::hasText)
                .toList();
    }
}
