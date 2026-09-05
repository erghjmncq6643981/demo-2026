package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 同一学习单元的新材料版本生成工作流。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneMaterialRegenerationTaskHandler implements AiTaskHandler {

    private final LearningPlanService planService;
    private final LearningSceneRelatedVocabularyService relatedVocabularyService;
    private final SceneArticleAudioService sceneArticleAudioService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_MATERIAL_REGENERATION;
    }

    /** 定义任务的执行步骤（4 步流水线：生成新版本 -> 扩充新版本相关词 -> 合成新版本语音 -> 切换生效版本）。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition("generate_revision", "生成材料新版本", 10),
                new AiTaskStepDefinition("generate_related_words", "生成新版本场景相关词汇", 20),
                new AiTaskStepDefinition("synthesize_audio", "合成新版本场景文章语音", 30),
                new AiTaskStepDefinition("publish_revision", "切换当前材料版本", 40));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        LocalDate date = AiTaskPayload.dateValue(payload, "recommendedDate", LocalDate.now());
        List<LearningPlanUnitResponse> regeneratedUnits = executionService.execute(task.getId(), "generate_revision", task.getOperatorUserId(), modelConfigId,
                () -> planService.regenerateDayUnits(task.getOwnerUserId(), task.getPlanId(), modelConfigId, date));
        taskService.updateProgress(task.getId(), 4, 1, 0);
        executionService.execute(task.getId(), "generate_related_words", task.getOperatorUserId(), modelConfigId, () -> {
            List<LearningPlanUnit> units = resolveUnits(task.getPlanId(), regeneratedUnits, date);
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
        taskService.updateProgress(task.getId(), 4, 2, 0);
        executionService.execute(task.getId(), "synthesize_audio", task.getOperatorUserId(), null, () -> {
            List<LearningPlanUnit> units = resolveUnits(task.getPlanId(), regeneratedUnits, date);
            if (units.isEmpty()) {
                return 0;
            }
            for (LearningPlanUnit unit : units) {
                try {
                    sceneArticleAudioService.generateOrGetSceneAudio(unit.getId(), true);
                } catch (Exception ex) {
                    log.warn("重新生成材料任务合成文章语音异常 unitId={}: {}", unit.getId(), ex.getMessage());
                }
            }
            return units.size();
        });
        taskService.updateProgress(task.getId(), 4, 3, 0);
        executionService.execute(task.getId(), "publish_revision", task.getOperatorUserId(), null,
                () -> planService.detail(task.getOwnerUserId(), task.getPlanId()));
        taskService.updateProgress(task.getId(), 4, 4, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }

    private List<LearningPlanUnit> resolveUnits(Long planId, List<LearningPlanUnitResponse> regeneratedUnits, LocalDate date) {
        if (regeneratedUnits != null && !regeneratedUnits.isEmpty()) {
            return planService.findUnitsByIds(planId,
                    regeneratedUnits.stream().map(LearningPlanUnitResponse::getId).toList());
        }
        return planService.findUnitsByDate(planId, date);
    }
}
