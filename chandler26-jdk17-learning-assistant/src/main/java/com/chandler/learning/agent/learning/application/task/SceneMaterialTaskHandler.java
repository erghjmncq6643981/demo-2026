package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            return runRelatedVocabulary(task, dateUnits, modelConfigId);
        });
        taskService.updateProgress(task.getId(), 4, 3, 0);

        // 步骤 4：合成场景文章语音（阿里云 TTS 预合成并落盘）
        executionService.execute(task.getId(), AUDIO, operator, null, () -> {
            List<LearningPlanUnit> dateUnits = resolveUnits(task.getPlanId(), generatedUnits, recommendedDate);
            return runAudio(task, dateUnits, true);
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

    /** 按单元记录相关词生成进度；单篇失败不会丢失已成功结果。 */
    private int runRelatedVocabulary(AiAsyncTask task, List<LearningPlanUnit> units, Long modelConfigId) {
        if (units.isEmpty()) {
            executionService.updateProgress(task.getId(), RELATED, 0, 0);
            return 0;
        }
        int completed = 0;
        List<String> failures = new java.util.ArrayList<>();
        executionService.updateProgress(task.getId(), RELATED, units.size(), 0);
        ensureTaskActive(task);
        for (LearningPlanUnit unit : units) {
            try {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
                completed++;
            } catch (RuntimeException ex) {
                failures.add("单元#" + unit.getId());
                log.warn("场景材料任务补充相关词失败 unitId={} errorType={}", unit.getId(), ex.getClass().getSimpleName());
            }
        }
        executionService.updateProgress(task.getId(), RELATED, units.size(), completed);
        if (!failures.isEmpty()) {
            taskService.updateProgress(task.getId(), 4, 2, 1);
        }
        throwIfFailures("场景相关词汇", failures, completed);
        return completed;
    }

    /** 按单元记录音频生成进度；失败单元会进入任务重试而不是被静默跳过。 */
    private int runAudio(AiAsyncTask task, List<LearningPlanUnit> units, boolean forceRefresh) {
        if (units.isEmpty()) {
            executionService.updateProgress(task.getId(), AUDIO, 0, 0);
            return 0;
        }
        Set<Long> completedUnitIds = readCompletedAudioUnitIds(task);
        Set<Long> currentUnitIds = units.stream().map(LearningPlanUnit::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        completedUnitIds.retainAll(currentUnitIds);
        int completed = completedUnitIds.size();
        List<String> failures = new java.util.ArrayList<>();
        executionService.updateProgress(task.getId(), AUDIO, units.size(), 0);
        ensureTaskActive(task);
        for (LearningPlanUnit unit : units) {
            if (unit.getId() != null && completedUnitIds.contains(unit.getId())) {
                continue;
            }
            try {
                sceneArticleAudioService.generateOrGetSceneAudio(unit.getId(), forceRefresh);
                if (unit.getId() != null) {
                    completedUnitIds.add(unit.getId());
                }
                completed++;
            } catch (RuntimeException ex) {
                failures.add("单元#" + unit.getId());
                log.warn("场景材料任务合成文章语音失败 unitId={} errorType={}", unit.getId(), ex.getClass().getSimpleName());
            }
        }
        saveCompletedAudioUnitIds(task, completedUnitIds);
        executionService.updateProgress(task.getId(), AUDIO, units.size(), completed);
        if (!failures.isEmpty()) {
            taskService.updateProgress(task.getId(), 4, 3, 1);
        }
        throwIfFailures("场景文章语音", failures, completed);
        return completed;
    }

    /** 读取音频步骤检查点，重试时只处理尚未成功的单元。 */
    private Set<Long> readCompletedAudioUnitIds(AiAsyncTask task) {
        Map<?, ?> checkpoint = executionService.getStepCheckpoint(task.getId(), AUDIO, Map.class);
        if (checkpoint == null || !(checkpoint.get("completedUnitIds") instanceof List<?> values)) {
            return new HashSet<>();
        }
        Set<Long> result = new HashSet<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                result.add(number.longValue());
            } else if (value != null) {
                try {
                    result.add(Long.valueOf(String.valueOf(value)));
                } catch (NumberFormatException ignored) {
                    log.debug("忽略无法识别的音频步骤检查点 unitId taskId={}", task.getId());
                }
            }
        }
        return result;
    }

    private void saveCompletedAudioUnitIds(AiAsyncTask task, Set<Long> completedUnitIds) {
        executionService.saveStepCheckpoint(task.getId(), AUDIO,
                Map.of("completedUnitIds", new ArrayList<>(completedUnitIds)));
    }

    private void ensureTaskActive(AiAsyncTask task) {
        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }
    }

    private void throwIfFailures(String label, List<String> failures, int completed) {
        if (failures.isEmpty()) {
            return;
        }
        throw LearningAssistantException.externalService(
                LearningErrorCode.EXTERNAL_SERVICE_CALL_FAILED,
                label + "有 " + failures.size() + " 篇失败，已完成 " + completed + " 篇，请重试失败步骤",
                null);
    }
}
