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
            return runRelatedVocabulary(task, units, modelConfigId);
        });
        taskService.updateProgress(task.getId(), 4, 2, 0);
        executionService.execute(task.getId(), "synthesize_audio", task.getOperatorUserId(), null, () -> {
            List<LearningPlanUnit> units = resolveUnits(task.getPlanId(), regeneratedUnits, date);
            return runAudio(task, units, true);
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

    /** 按单元保存相关词生成进度，重试时只会再次处理未达到目标的单元。 */
    private int runRelatedVocabulary(AiAsyncTask task, List<LearningPlanUnit> units, Long modelConfigId) {
        if (units.isEmpty()) {
            executionService.updateProgress(task.getId(), "generate_related_words", 0, 0);
            return 0;
        }
        int completed = 0;
        List<String> failures = new java.util.ArrayList<>();
        executionService.updateProgress(task.getId(), "generate_related_words", units.size(), 0);
        ensureTaskActive(task);
        for (LearningPlanUnit unit : units) {
            try {
                relatedVocabularyService.generate(task.getOwnerUserId(), task.getPlanId(), unit.getId(),
                        modelConfigId, LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT);
                completed++;
            } catch (RuntimeException ex) {
                failures.add("单元#" + unit.getId());
                log.warn("材料重生成任务补充相关词失败 unitId={} errorType={}", unit.getId(), ex.getClass().getSimpleName());
            }
        }
        executionService.updateProgress(task.getId(), "generate_related_words", units.size(), completed);
        if (!failures.isEmpty()) {
            taskService.updateProgress(task.getId(), 4, 1, 1);
        }
        throwIfFailures("场景相关词汇", failures, completed);
        return completed;
    }

    /** 按单元保存音频合成进度，失败单元由任务状态机统一重试。 */
    private int runAudio(AiAsyncTask task, List<LearningPlanUnit> units, boolean forceRefresh) {
        if (units.isEmpty()) {
            executionService.updateProgress(task.getId(), "synthesize_audio", 0, 0);
            return 0;
        }
        Set<Long> completedUnitIds = readCompletedAudioUnitIds(task);
        Set<Long> currentUnitIds = units.stream().map(LearningPlanUnit::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        completedUnitIds.retainAll(currentUnitIds);
        int completed = completedUnitIds.size();
        List<String> failures = new java.util.ArrayList<>();
        executionService.updateProgress(task.getId(), "synthesize_audio", units.size(), 0);
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
                log.warn("材料重生成任务合成文章语音失败 unitId={} errorType={}", unit.getId(), ex.getClass().getSimpleName());
            }
        }
        saveCompletedAudioUnitIds(task, completedUnitIds);
        executionService.updateProgress(task.getId(), "synthesize_audio", units.size(), completed);
        if (!failures.isEmpty()) {
            taskService.updateProgress(task.getId(), 4, 2, 1);
        }
        throwIfFailures("场景文章语音", failures, completed);
        return completed;
    }

    /** 读取音频步骤检查点，重试时只处理尚未成功的单元。 */
    private Set<Long> readCompletedAudioUnitIds(AiAsyncTask task) {
        Map<?, ?> checkpoint = executionService.getStepCheckpoint(task.getId(), "synthesize_audio", Map.class);
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
        executionService.saveStepCheckpoint(task.getId(), "synthesize_audio",
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
