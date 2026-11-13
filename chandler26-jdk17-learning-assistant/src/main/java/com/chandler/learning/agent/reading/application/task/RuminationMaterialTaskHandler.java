package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.application.RuminationAiGenerator;
import com.chandler.learning.agent.reading.application.RuminationAudioService;
import com.chandler.learning.agent.reading.application.RuminationMaterialPersistenceService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskPayload;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识反刍场景材料与微环境探针异步任务处理器。
 * <p>严格拆分为两个可恢复步骤：步骤 1 在事务外生成内容并保存检查点，步骤 2 幂等落库并按快照合并扣除词袋；
 * 若步骤 2 失败，重试时将跳过步骤 1 直接复用检查点，不再重复调用大模型。</p>
 */
@Slf4j
@Component
public class RuminationMaterialTaskHandler implements AiTaskHandler {

    public static final String STEP_GENERATE = "generate_content";
    public static final String STEP_PERSIST = "persist_material";

    private final RuminationAiGenerator aiGenerator;
    private final RuminationMaterialPersistenceService persistenceService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;
    private final RuminationAudioService ruminationAudioService;

    /** 兼容旧 4 参构造（供未注入音频清理服务的单元测试使用）。 */
    public RuminationMaterialTaskHandler(
            RuminationAiGenerator aiGenerator,
            RuminationMaterialPersistenceService persistenceService,
            AiTaskExecutionService executionService,
            AiAsyncTaskService taskService) {
        this(aiGenerator, persistenceService, executionService, taskService, null);
    }

    /** Spring 主构造函数：注入材料生成、落库、异步执行及覆盖再生后清理旧音频缓存的服务。 */
    @Autowired
    public RuminationMaterialTaskHandler(
            RuminationAiGenerator aiGenerator,
            RuminationMaterialPersistenceService persistenceService,
            AiTaskExecutionService executionService,
            AiAsyncTaskService taskService,
            RuminationAudioService ruminationAudioService) {
        this.aiGenerator = aiGenerator;
        this.persistenceService = persistenceService;
        this.executionService = executionService;
        this.taskService = taskService;
        this.ruminationAudioService = ruminationAudioService;
    }

    /** 返回当前处理器绑定的任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.RUMINATION_MATERIAL;
    }

    /** 定义两阶段可恢复步骤顺序。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(
                new AiTaskStepDefinition(STEP_GENERATE, "生成并校验场景材料与微环境探针", 10),
                new AiTaskStepDefinition(STEP_PERSIST, "幂等落库场景材料并结算词袋", 20)
        );
    }

    /** 执行或断点续跑知识反刍材料异步任务。 */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long wordbookId = AiTaskPayload.longValue(payload, "wordbookId");
        String scenarioCode = stringValue(payload, "scenarioCode", "");
        String scenarioName = stringValue(payload, "scenarioName", "场景星网");
        String scenarioPrompt = stringValue(payload, "scenarioPrompt", scenarioName + "日常情境与交流");
        List<String> targetWords = extractTargetWords(payload);
        Map<String, String> wordDefinitions = extractWordDefinitions(payload);
        Long modelConfigId = AiTaskPayload.longValue(payload, "modelConfigId");
        boolean consumeBag = booleanValue(payload, "consumeBag", true);
        Long sourceMaterialId = AiTaskPayload.longValue(payload, "sourceMaterialId");
        Long operator = task.getOperatorUserId();

        if (wordbookId == null || !StringUtils.hasText(scenarioCode) || targetWords.size() < 5) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "知识反刍异步任务快照参数不完整或词汇不足 5 个");
        }

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        // 步骤 1：在事务外调用默认大模型（失败自动降级）并把完整产物写入步骤检查点
        RuminationMaterialCheckpoint stepOneResult = executionService.execute(
                task.getId(), STEP_GENERATE, operator, modelConfigId, () -> {
                    RuminationMaterialCheckpoint existingCheckpoint = executionService.getStepCheckpoint(
                            task.getId(), STEP_GENERATE, RuminationMaterialCheckpoint.class);
                    if (existingCheckpoint != null && StringUtils.hasText(existingCheckpoint.article())) {
                        return existingCheckpoint;
                    }
                    RuminationAiGenerator.GeneratedMaterialResult generated = aiGenerator.generateMaterialAndProbes(
                            task.getOwnerUserId(), scenarioName, scenarioPrompt, targetWords, wordDefinitions);
                    RuminationMaterialCheckpoint checkpoint = toCheckpoint(generated);
                    executionService.saveStepCheckpoint(task.getId(), STEP_GENERATE, checkpoint);
                    return checkpoint;
                });
        taskService.updateProgress(task.getId(), 2, 1, 0);

        if (taskService.isCancelled(task.getId())) {
            throw new AiAsyncTaskCancelledException();
        }

        // 步骤 2：在独立短事务内幂等落库（新增或覆盖已有材料）并按提交时快照扣除词袋；重试时不重复调用步骤 1
        RuminationMaterialResponse persisted = executionService.execute(
                task.getId(), STEP_PERSIST, operator, null, () -> {
                    RuminationMaterialCheckpoint checkpoint = stepOneResult != null
                            ? stepOneResult
                            : executionService.getStepCheckpoint(
                                    task.getId(), STEP_GENERATE, RuminationMaterialCheckpoint.class);
                    if (checkpoint == null || !StringUtils.hasText(checkpoint.article())) {
                        throw LearningAssistantException.badRequest(
                                LearningErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND,
                                "未找到阶段 1 生成结果检查点，请重新提交生成任务");
                    }
                    return persistenceService.persistGeneratedMaterial(
                            task.getOwnerUserId(),
                            wordbookId,
                            scenarioCode,
                            targetWords,
                            checkpoint.generationSource(),
                            task.getId(),
                            checkpoint.toGeneratedResult(),
                            consumeBag,
                            sourceMaterialId);
                });

        if (persisted == null) {
            persisted = persistenceService.findMaterialResponseByTaskId(task.getOwnerUserId(), task.getId());
        }
        if (persisted != null && persisted.getBagConsumed() == null) {
            persisted.setBagConsumed(consumeBag);
        }
        if (persisted != null && StringUtils.hasText(persisted.getId())) {
            if (sourceMaterialId != null && ruminationAudioService != null) {
                try {
                    ruminationAudioService.invalidateStoredAudio(Long.valueOf(persisted.getId()));
                } catch (Exception ex) {
                    log.warn("覆盖重新生成材料后清理过期音频失败，materialId={}, reason={}",
                            persisted.getId(), ex.getMessage());
                }
            }
            taskService.bindBusiness(task.getId(), AiTaskConstants.TYPE_RUMINATION_MATERIAL, persisted.getId());
        }
        taskService.updateProgress(task.getId(), 2, 2, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
    }

    private RuminationMaterialCheckpoint toCheckpoint(RuminationAiGenerator.GeneratedMaterialResult generated) {
        String materialSource = generated.aiPowered() ? "ai" : "fallback";
        List<RuminationMaterialCheckpoint.ProbeCheckpoint> probes = generated.probes() == null
                ? List.of()
                : generated.probes().stream()
                        .map(p -> new RuminationMaterialCheckpoint.ProbeCheckpoint(
                                p.targetWord(), p.probeType(), p.question(),
                                p.options(), p.correctAnswer(), p.hint(),
                                generated.aiPowered() && StringUtils.hasText(p.generationSource())
                                        ? p.generationSource()
                                        : materialSource))
                        .toList();
        return new RuminationMaterialCheckpoint(
                materialSource,
                generated.title(),
                generated.summary(),
                generated.article(),
                generated.translation(),
                probes
        );
    }

    private boolean booleanValue(Map<String, Object> payload, String key, boolean defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return defaultValue;
    }

    private String stringValue(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    private List<String> extractTargetWords(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("targetWords");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> item.toString().trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, String> extractWordDefinitions(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("wordDefinitions");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (k != null && v != null) {
                result.put(k.toString().toLowerCase(Locale.ROOT), v.toString());
            }
        });
        return result;
    }
}
