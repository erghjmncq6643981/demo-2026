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

/** 学习域拥有的场景材料分步生成工作流。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneMaterialTaskHandler implements AiTaskHandler {

    private static final String PREPARE = "prepare_vocabulary";
    private static final String MATERIAL = "generate_material";
    private static final String RELATED = "generate_related_words";
    private static final String AUDIO = "synthesize_audio";

    private final LearningPlanService planService;
    private final LearningSceneRelatedVocabularyService relatedVocabularyService;
    private final SceneArticleAudioService sceneArticleAudioService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回处理器支持的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_MATERIAL;
    }

    /** 定义任务的执行步骤（4 步流水线：选词 -> 生成材料与核心词 -> 扩充相关词 -> 合成文章语音）。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition(PREPARE, "确定学习词组", 10),
                new AiTaskStepDefinition(MATERIAL, "生成场景文章与核心词数据", 20),
                new AiTaskStepDefinition(RELATED, "补充场景相关词汇", 30),
                new AiTaskStepDefinition(AUDIO, "合成场景文章语音", 40));
    }

    /** 执行当前任务处理流程。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        LocalDate recommendedDate = AiTaskPayload.dateValue(payload, "recommendedDate", LocalDate.now());
        Long operator = task.getOperatorUserId();

        // 步骤 1：确定学习词组（极短事务锁选词并写入 Checkpoint，立即释放锁）
        executionService.execute(task.getId(), PREPARE, operator, null,
                () -> planService.prepareVocabularyForTask(task.getOwnerUserId(), task.getPlanId(),
                        recommendedDate, task.getId()));
        taskService.updateProgress(task.getId(), 4, 1, 0);

        // 步骤 2：生成场景文章与核心词数据（读取词组，无锁并发调用 AI 撰写故事并落库）
        List<LearningPlanUnitResponse> generatedUnits = executionService.execute(task.getId(), MATERIAL, operator, modelConfigId,
                () -> planService.generateMaterialForTask(task.getOwnerUserId(), task.getPlanId(), modelConfigId,
                        recommendedDate, task.getId()));
        taskService.updateProgress(task.getId(), 4, 2, 0);

        // 步骤 3：补充场景相关词汇（为生成好的单元扩充 50 个相关词）
        executionService.execute(task.getId(), RELATED, operator, modelConfigId, () -> {
            List<LearningPlanUnit> dateUnits = resolveUnits(task.getPlanId(), generatedUnits, recommendedDate);
            if (dateUnits.isEmpty()) {
                return 0;
            }
            if (dateUnits.size() == 1) {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), dateUnits.get(0).getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
            } else {
                dateUnits.parallelStream().forEach(unit ->
                        relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                                modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT));
            }
            return dateUnits.size();
        });
        taskService.updateProgress(task.getId(), 4, 3, 0);

        // 步骤 4：合成场景文章语音（阿里云 TTS 预合成并落盘）
        executionService.execute(task.getId(), AUDIO, operator, null, () -> {
            List<LearningPlanUnit> dateUnits = resolveUnits(task.getPlanId(), generatedUnits, recommendedDate);
            if (dateUnits.isEmpty()) {
                return 0;
            }
            for (LearningPlanUnit unit : dateUnits) {
                try {
                    sceneArticleAudioService.generateOrGetSceneAudio(unit.getId(), true);
                } catch (Exception ex) {
                    log.warn("场景材料任务合成文章语音异常 unitId={}: {}", unit.getId(), ex.getMessage());
                }
            }
            return dateUnits.size();
        });
        taskService.updateProgress(task.getId(), 4, 4, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }

    private List<LearningPlanUnit> resolveUnits(Long planId, List<LearningPlanUnitResponse> generatedUnits, LocalDate date) {
        if (generatedUnits != null && !generatedUnits.isEmpty()) {
            return planService.findUnitsByIds(planId,
                    generatedUnits.stream().map(LearningPlanUnitResponse::getId).toList());
        }
        return planService.findUnitsByDate(planId, date);
    }
}
