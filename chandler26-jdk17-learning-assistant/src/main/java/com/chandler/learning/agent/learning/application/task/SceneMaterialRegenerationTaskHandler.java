package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.support.LearningConstants;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.AiAsyncTask;
import com.chandler.learning.agent.task.domain.AiTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 同一学习单元的新材料版本生成工作流。 */
@Component
@RequiredArgsConstructor
public class SceneMaterialRegenerationTaskHandler implements AiTaskHandler {

    private final LearningPlanService planService;
    private final LearningSceneRelatedVocabularyService relatedVocabularyService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_MATERIAL_REGENERATION;
    }

    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition("generate_revision", "生成材料新版本", 10),
                new AiTaskStepDefinition("generate_related_words", "生成新版本场景相关词汇", 20),
                new AiTaskStepDefinition("publish_revision", "切换当前材料版本", 30));
    }

    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        LocalDate date = AiTaskPayload.dateValue(payload, "recommendedDate", LocalDate.now());
        executionService.execute(task.getId(), "generate_revision", task.getOperatorUserId(), modelConfigId,
                () -> planService.regenerateDayUnits(task.getOwnerUserId(), task.getPlanId(), modelConfigId, date));
        taskService.updateProgress(task.getId(), 3, 1, 0);
        executionService.execute(task.getId(), "generate_related_words", task.getOperatorUserId(), modelConfigId, () -> {
            LearningPlanResponse plan = planService.detail(task.getOwnerUserId(), task.getPlanId());
            List<LearningPlanUnitResponse> units = plan.getUnits().stream()
                    .filter(unit -> date.equals(unit.getRecommendedDate()))
                    .toList();
            for (LearningPlanUnitResponse unit : units) {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
            }
            return units.size();
        });
        taskService.updateProgress(task.getId(), 3, 2, 0);
        executionService.execute(task.getId(), "publish_revision", task.getOperatorUserId(), null,
                () -> planService.detail(task.getOwnerUserId(), task.getPlanId()));
        taskService.updateProgress(task.getId(), 3, 3, 0);
        taskService.complete(task.getId(), LearningConstants.AiTask.STATUS_COMPLETED, null);
    }
}
