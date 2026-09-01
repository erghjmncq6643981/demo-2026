package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
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

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_MATERIAL_REGENERATION;
    }

    /** 定义任务的执行步骤。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition("generate_revision", "生成材料新版本", 10),
                new AiTaskStepDefinition("generate_related_words", "生成新版本场景相关词汇", 20),
                new AiTaskStepDefinition("publish_revision", "切换当前材料版本", 30));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        LocalDate date = AiTaskPayload.dateValue(payload, "recommendedDate", LocalDate.now());
        List<LearningPlanUnitResponse> regeneratedUnits = executionService.execute(task.getId(), "generate_revision", task.getOperatorUserId(), modelConfigId,
                () -> planService.regenerateDayUnits(task.getOwnerUserId(), task.getPlanId(), modelConfigId, date));
        taskService.updateProgress(task.getId(), 3, 1, 0);
        executionService.execute(task.getId(), "generate_related_words", task.getOperatorUserId(), modelConfigId, () -> {
            List<LearningPlanUnit> units;
            if (regeneratedUnits != null && !regeneratedUnits.isEmpty()) {
                units = planService.findUnitsByIds(task.getPlanId(),
                        regeneratedUnits.stream().map(LearningPlanUnitResponse::getId).toList());
            } else {
                units = planService.findUnitsByDate(task.getPlanId(), date);
            }
            if (units.isEmpty()) {
                return 0;
            }
            if (units.size() == 1) {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), units.get(0).getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
            } else {
                units.parallelStream().forEach(unit ->
                        relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                                modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT));
            }
            return units.size();
        });
        taskService.updateProgress(task.getId(), 3, 2, 0);
        executionService.execute(task.getId(), "publish_revision", task.getOperatorUserId(), null,
                () -> planService.detail(task.getOwnerUserId(), task.getPlanId()));
        taskService.updateProgress(task.getId(), 3, 3, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }
}
