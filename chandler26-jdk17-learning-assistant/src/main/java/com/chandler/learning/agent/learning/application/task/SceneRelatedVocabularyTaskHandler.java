package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 已有场景材料缺少相关词时的独立补生成任务。 */
@Component
@RequiredArgsConstructor
public class SceneRelatedVocabularyTaskHandler implements AiTaskHandler {

    private final LearningSceneRelatedVocabularyService relatedVocabularyService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_RELATED_VOCABULARY;
    }

    /** 定义任务的执行步骤。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition("generate_related_words", "补充场景相关词汇", 10));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        Long unitId = task.getUnitId() != null ? task.getUnitId() : AiTaskPayload.longValue(payload, "unitId");
        Long planId = task.getPlanId() != null ? task.getPlanId() : AiTaskPayload.longValue(payload, "planId");
        int targetCount = AiTaskPayload.intValue(payload, "targetCount",
                LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
        executionService.execute(task.getId(), "generate_related_words", task.getOperatorUserId(), modelConfigId,
                () -> relatedVocabularyService.generate(task.getOwnerUserId(), planId, unitId,
                        modelConfigId, targetCount));
        if (unitId != null) {
            taskService.bindBusiness(task.getId(), "scene_unit", String.valueOf(unitId));
        }
        taskService.updateProgress(task.getId(), targetCount, targetCount, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }
}
