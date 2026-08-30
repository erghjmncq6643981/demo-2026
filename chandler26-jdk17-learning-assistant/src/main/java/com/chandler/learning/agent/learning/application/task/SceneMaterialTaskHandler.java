package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 学习域拥有的场景材料分步生成工作流。 */
@Component
@RequiredArgsConstructor
public class SceneMaterialTaskHandler implements AiTaskHandler {

    private static final String PREPARE = "prepare_vocabulary";
    private static final String MATERIAL = "generate_material";
    private static final String RELATED = "generate_related_words";
    private static final String PUBLISH = "publish_material";

    private final LearningPlanService planService;
    private final LearningSceneRelatedVocabularyService relatedVocabularyService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_MATERIAL;
    }

    /** 定义任务的执行步骤。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition(PREPARE, "确定学习词组", 10),
                new AiTaskStepDefinition(MATERIAL, "生成场景文章与核心词数据", 20),
                new AiTaskStepDefinition(RELATED, "补充场景相关词汇", 30),
                new AiTaskStepDefinition(PUBLISH, "发布学习材料", 40));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        LocalDate recommendedDate = AiTaskPayload.dateValue(payload, "recommendedDate", LocalDate.now());
        Long operator = task.getOperatorUserId();
        executionService.execute(task.getId(), PREPARE, operator, null,
                () -> planService.detail(task.getOwnerUserId(), task.getPlanId()));
        taskService.updateProgress(task.getId(), 4, 1, 0);
        executionService.execute(task.getId(), MATERIAL, operator, modelConfigId,
                () -> planService.generateNextUnit(task.getOwnerUserId(), task.getPlanId(), modelConfigId,
                        recommendedDate, task.getId()));
        taskService.updateProgress(task.getId(), 4, 2, 0);
        executionService.execute(task.getId(), RELATED, operator, modelConfigId, () -> {
            LearningPlanResponse plan = planService.detail(task.getOwnerUserId(), task.getPlanId());
            List<LearningPlanUnitResponse> dateUnits = plan.getUnits().stream()
                    .filter(unit -> recommendedDate.equals(unit.getRecommendedDate()))
                    .toList();
            for (LearningPlanUnitResponse unit : dateUnits) {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
            }
            return dateUnits.size();
        });
        taskService.updateProgress(task.getId(), 4, 3, 0);
        executionService.execute(task.getId(), PUBLISH, operator, null,
                () -> planService.detail(task.getOwnerUserId(), task.getPlanId()));
        taskService.updateProgress(task.getId(), 4, 4, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }
}
