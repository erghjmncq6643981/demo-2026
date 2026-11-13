package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationCustomScenarioRequest;
import com.chandler.learning.agent.reading.api.request.RuminationFissionRequest;
import com.chandler.learning.agent.reading.api.response.RuminationAsyncTaskItemResponse;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
import com.chandler.learning.agent.reading.application.task.RuminationFissionCheckpoint;
import com.chandler.learning.agent.reading.application.task.RuminationFissionTaskHandler;
import com.chandler.learning.agent.reading.application.task.RuminationMaterialCheckpoint;
import com.chandler.learning.agent.reading.application.task.RuminationMaterialTaskHandler;
import com.chandler.learning.agent.reading.application.task.RuminationScenarioCheckpoint;
import com.chandler.learning.agent.reading.application.task.RuminationScenarioTaskHandler;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 知识反刍自定义场景、节点 4D 裂变异步任务提交与统一任务追踪服务。
 * <p>确保 Batch 2 两类入口复用统一的 {@link AiAsyncTaskService} 契约、幂等控制及跨场景聚合状态恢复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationGraphTaskSubmissionService {

    private final WordbookService wordbookService;
    private final RuminationService ruminationService;
    private final RuminationAiGenerator aiGenerator;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final AiTaskExecutionService executionService;
    private final ObjectMapper objectMapper;

    /**
     * 校验单词本归属后提交异步自定义场景构建任务，支持 requestId 与活动场景签名幂等复用。
     *
     * @param userId  当前用户 ID
     * @param request 自定义场景创建请求
     * @return 提交或幂等复用的 AI 异步任务详情
     */
    public AiAsyncTaskResponse submitCustomScenarioTask(Long userId, RuminationCustomScenarioRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        String scenarioCode = safeTrim(request.getScenarioCode());
        String scenarioName = safeTrim(request.getScenarioName());
        if (!StringUtils.hasText(scenarioCode) || !StringUtils.hasText(scenarioName)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "场景编码与场景名称不能为空");
        }
        String scenarioIcon = StringUtils.hasText(request.getScenarioIcon())
                ? request.getScenarioIcon().trim() : "🏷️";
        String scenarioPrompt = StringUtils.hasText(request.getScenarioPrompt())
                ? request.getScenarioPrompt().trim() : scenarioName;

        if (StringUtils.hasText(request.getRequestId())) {
            String requestKey = buildRequestKey("rum_sc_req", userId, request.getRequestId());
            AiAsyncTask existingByReq = aiAsyncTaskService.findLatestByIdempotencyKey(
                    userId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, requestKey);
            if (existingByReq != null) {
                return aiAsyncTaskService.toDetailResponse(existingByReq);
            }
        }

        String contentKey = "rum_sc:" + userId + ":" + sha256Hex(
                wordbook.getId() + "|" + scenarioCode, 32);
        AiAsyncTask activeByKey = aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, null, contentKey);
        if (activeByKey != null) {
            return aiAsyncTaskService.toDetailResponse(activeByKey);
        }

        AiAsyncTask latestSameCode = aiAsyncTaskService.findLatestByJobAndScenario(
                userId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, wordbook.getId(), scenarioCode);
        if (latestSameCode != null && (isActiveTask(latestSameCode)
                || AiTaskConstants.STATUS_COMPLETED.equals(latestSameCode.getStatus()))) {
            return aiAsyncTaskService.toDetailResponse(latestSameCode);
        }

        Long modelConfigId = aiGenerator.resolveDefaultModelConfigId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wordbookId", String.valueOf(wordbook.getId()));
        payload.put("scenarioCode", scenarioCode);
        payload.put("scenarioName", scenarioName);
        payload.put("scenarioIcon", scenarioIcon);
        payload.put("scenarioPrompt", scenarioPrompt);
        if (modelConfigId != null) {
            payload.put("modelConfigId", modelConfigId);
        }

        String idempotencyKey = StringUtils.hasText(request.getRequestId())
                ? buildRequestKey("rum_sc_req", userId, request.getRequestId()) : contentKey;
        String taskName = "知识反刍自定义场景：" + scenarioIcon + " " + scenarioName;
        AiAsyncTask created = aiAsyncTaskService.create(
                userId,
                AiTaskConstants.TYPE_RUMINATION_SCENARIO,
                taskName,
                null,
                null,
                wordbook.getId(),
                AiTaskConstants.EXECUTION_IMMEDIATE,
                null,
                AiTaskConstants.DEFAULT_PRIORITY,
                2,
                idempotencyKey,
                payload
        );
        log.info("用户「{}」提交自定义场景异步构建任务 taskId={} wordbookId={} scenario={}",
                userId, created.getId(), wordbook.getId(), scenarioCode);
        return aiAsyncTaskService.toDetailResponse(created);
    }

    /**
     * 校验词本、场景归属与父节点有效性后提交异步节点 4D 衍生裂变任务，并将上下文快照写入任务载荷。
     *
     * @param userId  当前用户 ID
     * @param request 节点衍生裂变请求
     * @return 提交或幂等复用的 AI 异步任务详情
     */
    public AiAsyncTaskResponse submitFissionTask(Long userId, RuminationFissionRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        String scenarioCode = safeTrim(request.getScenarioCode());
        String targetWord = safeTrim(request.getTargetWord());
        String parentNodeId = StringUtils.hasText(request.getParentNodeId())
                ? request.getParentNodeId().trim() : targetWord;

        if (!StringUtils.hasText(scenarioCode) || !StringUtils.hasText(targetWord)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "场景编码与待裂变目标词汇不能为空");
        }

        String[] scenarioMeta = ruminationService.resolveOwnedScenarioMeta(userId, wordbook.getId(), scenarioCode);
        String scenarioName = StringUtils.hasText(request.getScenarioName())
                ? request.getScenarioName().trim() : scenarioMeta[0];
        ruminationService.assertParentNodeExistsIfSaved(userId, wordbook.getId(), scenarioCode, parentNodeId);

        if (StringUtils.hasText(request.getRequestId())) {
            String requestKey = buildRequestKey("rum_fis_req", userId, request.getRequestId());
            AiAsyncTask existingByReq = aiAsyncTaskService.findLatestByIdempotencyKey(
                    userId, AiTaskConstants.TYPE_RUMINATION_FISSION, requestKey);
            if (existingByReq != null) {
                return aiAsyncTaskService.toDetailResponse(existingByReq);
            }
        }

        String contentSig = sha256Hex(
                wordbook.getId() + "|" + scenarioCode + "|" + parentNodeId + "|"
                        + targetWord.toLowerCase(Locale.ROOT), 32);
        String contentKey = "rum_fis:" + userId + ":" + contentSig;
        AiAsyncTask activeByKey = aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_FISSION, null, contentKey);
        if (activeByKey != null) {
            return aiAsyncTaskService.toDetailResponse(activeByKey);
        }

        List<String> existingTerms = request.getExistingTerms() == null ? List.of() : request.getExistingTerms().stream()
                .filter(StringUtils::hasText)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        Long modelConfigId = aiGenerator.resolveDefaultModelConfigId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wordbookId", String.valueOf(wordbook.getId()));
        payload.put("scenarioCode", scenarioCode);
        payload.put("scenarioName", scenarioName);
        payload.put("parentNodeId", parentNodeId);
        payload.put("targetWord", targetWord);
        payload.put("targetDefinition", safeTrim(request.getTargetDefinition()));
        payload.put("existingTerms", existingTerms);
        payload.put("contentSignature", contentSig);
        if (modelConfigId != null) {
            payload.put("modelConfigId", modelConfigId);
        }

        String idempotencyKey = StringUtils.hasText(request.getRequestId())
                ? buildRequestKey("rum_fis_req", userId, request.getRequestId()) : contentKey;
        String taskName = "知识反刍 4D 裂变：" + targetWord + "（" + scenarioName + "）";
        AiAsyncTask created = aiAsyncTaskService.create(
                userId,
                AiTaskConstants.TYPE_RUMINATION_FISSION,
                taskName,
                null,
                null,
                wordbook.getId(),
                AiTaskConstants.EXECUTION_IMMEDIATE,
                null,
                AiTaskConstants.DEFAULT_PRIORITY,
                2,
                idempotencyKey,
                payload
        );
        log.info("用户「{}」提交节点裂变异步任务 taskId={} wordbookId={} scenario={} parent={} word={}",
                userId, created.getId(), wordbook.getId(), scenarioCode, parentNodeId, targetWord);
        return aiAsyncTaskService.toDetailResponse(created);
    }

    /**
     * 查询指定单词本下三类知识反刍任务列表（返回全部活动任务及当前场景最近失败/完成项），用于跨场景追踪与刷新恢复。
     *
     * @param userId       当前用户 ID
     * @param wordbookId   当前单词本 ID
     * @param scenarioCode 当前展示的场景编码（可选）
     * @return 任务摘要与绑定上下文列表
     */
    public List<RuminationAsyncTaskItemResponse> listRuminationTasks(Long userId, Long wordbookId, String scenarioCode) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, wordbookId);
        List<AiAsyncTask> tasks = aiAsyncTaskService.findRecentByRelatedJobAndTypes(
                userId,
                wordbook.getId(),
                List.of(
                        AiTaskConstants.TYPE_RUMINATION_MATERIAL,
                        AiTaskConstants.TYPE_RUMINATION_SCENARIO,
                        AiTaskConstants.TYPE_RUMINATION_FISSION
                ),
                20
        );
        String trimmedScenario = safeTrim(scenarioCode);
        return tasks.stream()
                .map(task -> toTaskItemResponse(wordbook.getId(), task))
                .filter(item -> {
                    String status = item.getTask().getStatus();
                    if (isActiveStatus(status)) {
                        return true;
                    }
                    if (!StringUtils.hasText(trimmedScenario)) {
                        return true;
                    }
                    if (trimmedScenario.equals(item.getScenarioCode())) {
                        return true;
                    }
                    // 对自定义场景构建刚失败/刚完成任务也在切回其他场景时允许展示最近通知/重试入口
                    return AiTaskConstants.TYPE_RUMINATION_SCENARIO.equals(item.getTask().getTaskType())
                            && List.of(AiTaskConstants.STATUS_FAILED, AiTaskConstants.STATUS_ATTENTION_REQUIRED).contains(status);
                })
                .toList();
    }

    /**
     * 读取节点 4D 衍生裂变异步任务阶段 2 的结构化应用结果，确保前端仅在原场景原父节点仍存在时更新画布。
     *
     * @param userId 当前用户 ID
     * @param taskId 异步任务 ID
     * @return 裂变应用结果及新增节点、连线列表
     */
    public RuminationFissionResponse getFissionTaskResult(Long userId, Long taskId) {
        AiAsyncTask task = aiAsyncTaskService.require(userId, taskId);
        if (!AiTaskConstants.TYPE_RUMINATION_FISSION.equals(task.getTaskType())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_ASYNC_TASK_TYPE_INVALID, "任务类型不是节点衍生裂变");
        }
        RuminationFissionResponse persisted = executionService.getStepCheckpoint(
                taskId, RuminationFissionTaskHandler.STEP_PERSIST, RuminationFissionResponse.class);
        if (persisted == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.AI_ASYNC_TASK_STEP_NOT_FOUND, "衍生裂变任务尚未完成落库");
        }
        return persisted;
    }

    private RuminationAsyncTaskItemResponse toTaskItemResponse(Long wordbookId, AiAsyncTask task) {
        RuminationAsyncTaskItemResponse item = new RuminationAsyncTaskItemResponse();
        item.setTask(aiAsyncTaskService.toDetailResponse(task));
        item.setWordbookId(String.valueOf(wordbookId));
        if (StringUtils.hasText(task.getPayloadJson())) {
            try {
                JsonNode payload = objectMapper.readTree(task.getPayloadJson());
                item.setScenarioCode(payload.path("scenarioCode").asText(""));
                item.setScenarioName(payload.path("scenarioName").asText(""));
                item.setScenarioIcon(payload.path("scenarioIcon").asText(""));
                item.setParentNodeId(payload.path("parentNodeId").asText(""));
                item.setTargetWord(payload.path("targetWord").asText(""));
            } catch (Exception ignored) {}
        }
        item.setGenerationSource(resolveGenerationSource(task));
        return item;
    }

    private String resolveGenerationSource(AiAsyncTask task) {
        if (AiTaskConstants.TYPE_RUMINATION_MATERIAL.equals(task.getTaskType())) {
            RuminationMaterialCheckpoint cp = executionService.getStepCheckpoint(
                    task.getId(), RuminationMaterialTaskHandler.STEP_GENERATE, RuminationMaterialCheckpoint.class);
            return cp == null ? null : cp.generationSource();
        }
        if (AiTaskConstants.TYPE_RUMINATION_SCENARIO.equals(task.getTaskType())) {
            RuminationScenarioCheckpoint cp = executionService.getStepCheckpoint(
                    task.getId(), RuminationScenarioTaskHandler.STEP_GENERATE, RuminationScenarioCheckpoint.class);
            return cp == null ? null : cp.generationSource();
        }
        if (AiTaskConstants.TYPE_RUMINATION_FISSION.equals(task.getTaskType())) {
            RuminationFissionCheckpoint cp = executionService.getStepCheckpoint(
                    task.getId(), RuminationFissionTaskHandler.STEP_GENERATE, RuminationFissionCheckpoint.class);
            return cp == null ? null : cp.generationSource();
        }
        return null;
    }

    private boolean isActiveTask(AiAsyncTask task) {
        return task != null && isActiveStatus(task.getStatus());
    }

    private boolean isActiveStatus(String status) {
        return List.of(
                AiTaskConstants.STATUS_PENDING,
                AiTaskConstants.STATUS_RUNNING,
                AiTaskConstants.STATUS_RETRY_WAIT).contains(status);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildRequestKey(String prefix, Long userId, String requestId) {
        String raw = prefix + ":" + userId + ":" + requestId.trim();
        return raw.length() <= 160 ? raw : prefix + ":" + userId + ":" + sha256Hex(requestId.trim(), 32);
    }

    private String sha256Hex(String text, int maxHexChars) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return hex.substring(0, Math.min(maxHexChars, hex.length()));
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
