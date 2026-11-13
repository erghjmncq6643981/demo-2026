package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationMaterialGenerateRequest;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
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
 * 知识反刍场景材料与探针异步生成提交服务。
 * <p>负责在提交期校验词本与场景归属、规范化目标词袋快照、绑定默认模型并实现提交幂等复用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationMaterialTaskSubmissionService {

    private final WordbookService wordbookService;
    private final RuminationService ruminationService;
    private final RuminationAiGenerator aiGenerator;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final ObjectMapper objectMapper;

    /**
     * 校验词本、场景归属和词袋目标词数量后保存输入快照并提交异步生成任务。
     * <p>同一次提交重发（相同 requestId 或相同活动快照指纹）直接返回已有任务，避免重复扣费或生成两份材料。</p>
     *
     * @param userId  当前用户 ID
     * @param request 编织场景材料请求参数
     * @return 提交或幂等复用的 AI 异步任务详情
     */
    public AiAsyncTaskResponse submitGenerateTask(Long userId, RuminationMaterialGenerateRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        String scenarioCode = request.getScenarioCode() == null ? "" : request.getScenarioCode().trim();
        if (!StringUtils.hasText(scenarioCode)) {
            throw LearningAssistantException.badRequest(LearningErrorCode.ARTICLE_WORDS_INVALID, "场景编码不能为空");
        }

        List<String> snapshotTargetWords = ruminationService.normalizeDistinctTargetWords(request.getTargetWords());
        if (snapshotTargetWords.size() < 5) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "词袋中目标词汇去重后至少需要 5 个才能编织场景材料");
        }

        String difficulty = normalizeOptional(request.getDifficulty(), "medium");
        String wordCountRange = normalizeOptional(request.getWordCountRange(), "150-200");
        String contentSignature = buildContentSignature(
                wordbook.getId(), scenarioCode, difficulty, wordCountRange, snapshotTargetWords);
        String contentKey = "rum_mat:" + userId + ":" + contentSignature;

        return submitTaskInternal(
                userId, wordbook, scenarioCode, snapshotTargetWords, difficulty, wordCountRange,
                contentSignature, request.getRequestId(), contentKey, true, null, "知识反刍材料：");
    }

    /**
     * 基于已有场景材料异步重新生成一份新文章与纯英文微环境探针。
     * <p>复用原材料的单词本、场景与目标词汇，保留历史材料记录且不扣除当前图谱词袋词汇。</p>
     *
     * @param userId     当前用户 ID
     * @param materialId 源场景材料 ID
     * @param requestId  可选幂等请求标识
     * @return 提交或幂等复用的 AI 异步任务详情
     */
    public AiAsyncTaskResponse submitRegenerateTask(Long userId, Long materialId, String requestId) {
        LearningRuminationMaterial sourceMaterial = ruminationService.requireOwnedMaterial(userId, materialId);
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, sourceMaterial.getWordbookId());
        String scenarioCode = sourceMaterial.getScenarioCode() == null ? "" : sourceMaterial.getScenarioCode().trim();
        if (!StringUtils.hasText(scenarioCode)) {
            throw LearningAssistantException.badRequest(LearningErrorCode.ARTICLE_WORDS_INVALID, "历史材料的场景编码缺失");
        }

        List<String> snapshotTargetWords = ruminationService.readMaterialTargetWords(sourceMaterial);
        if (snapshotTargetWords.size() < 5) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "历史材料目标词汇去重后不足 5 个，无法重新生成");
        }

        String difficulty = "medium";
        String wordCountRange = "150-200";
        String contentSignature = buildContentSignature(
                wordbook.getId(), scenarioCode, difficulty, wordCountRange, snapshotTargetWords);
        String activeRegenKey = "rum_mat_regen:" + userId + ":" + sourceMaterial.getId();

        return submitTaskInternal(
                userId, wordbook, scenarioCode, snapshotTargetWords, difficulty, wordCountRange,
                contentSignature, requestId, activeRegenKey, false, sourceMaterial.getId(), "重新生成知识反刍材料：");
    }

    private AiAsyncTaskResponse submitTaskInternal(
            Long userId,
            LearningWordbook wordbook,
            String scenarioCode,
            List<String> snapshotTargetWords,
            String difficulty,
            String wordCountRange,
            String contentSignature,
            String requestId,
            String activeDedupeKey,
            boolean consumeBag,
            Long sourceMaterialId,
            String taskNamePrefix) {
        String[] scenarioMeta = ruminationService.resolveOwnedScenarioMeta(userId, wordbook.getId(), scenarioCode);
        String scenarioName = scenarioMeta[0];
        String scenarioPrompt = scenarioMeta[1];

        // 1. 若客户端携带 requestId，则在所有非删除状态中命中复用（覆盖极快完成时的网络重发）
        String requestKey = null;
        if (StringUtils.hasText(requestId)) {
            requestKey = buildRequestIdempotencyKey(userId, requestId);
            AiAsyncTask existingByRequest = aiAsyncTaskService.findLatestByIdempotencyKey(
                    userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, requestKey);
            if (existingByRequest != null) {
                log.info("知识反刍异步生成命中 requestId 幂等复用 userId={} taskId={} status={}",
                        userId, existingByRequest.getId(), existingByRequest.getStatus());
                return aiAsyncTaskService.toDetailResponse(existingByRequest);
            }
        }

        // 2. 检查同用户、同词本与同场景下的活动排队/执行中任务，阻止双击并发建单与重复计费
        AiAsyncTask activeByKey = aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, null, activeDedupeKey);
        if (activeByKey != null) {
            log.info("知识反刍异步生成命中活动去重任务复用 userId={} taskId={}", userId, activeByKey.getId());
            return aiAsyncTaskService.toDetailResponse(activeByKey);
        }

        AiAsyncTask latestInScenario = aiAsyncTaskService.findLatestByJobAndScenario(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, wordbook.getId(), scenarioCode);
        if (isActiveTask(latestInScenario) && matchesActiveDuplicate(latestInScenario, contentSignature, sourceMaterialId, consumeBag)) {
            log.info("知识反刍异步生成命中场景活动快照任务复用 userId={} taskId={}", userId, latestInScenario.getId());
            return aiAsyncTaskService.toDetailResponse(latestInScenario);
        }

        // 3. 冻结提交期词义与模型快照
        Map<String, String> wordDefinitions = ruminationService.snapshotWordDefinitions(
                userId, wordbook.getId(), snapshotTargetWords);
        Long modelConfigId = aiGenerator.resolveDefaultModelConfigId();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("wordbookId", String.valueOf(wordbook.getId()));
        payload.put("scenarioCode", scenarioCode);
        payload.put("scenarioName", scenarioName);
        payload.put("scenarioPrompt", scenarioPrompt);
        payload.put("targetWords", snapshotTargetWords);
        payload.put("wordDefinitions", wordDefinitions);
        payload.put("difficulty", difficulty);
        payload.put("wordCountRange", wordCountRange);
        payload.put("contentSignature", contentSignature);
        payload.put("consumeBag", consumeBag);
        if (sourceMaterialId != null) {
            payload.put("sourceMaterialId", String.valueOf(sourceMaterialId));
        }
        if (modelConfigId != null) {
            payload.put("modelConfigId", modelConfigId);
        }

        String idempotencyKey = requestKey != null ? requestKey : activeDedupeKey;
        String taskName = taskNamePrefix + scenarioName + "（" + snapshotTargetWords.size() + " 词）";
        AiAsyncTask created = aiAsyncTaskService.create(
                userId,
                AiTaskConstants.TYPE_RUMINATION_MATERIAL,
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
        log.info("用户「{}」提交知识反刍异步材料任务 taskId={} wordbookId={} scenario={} words={} consumeBag={} sourceMaterialId={}",
                userId, created.getId(), wordbook.getId(), scenarioCode, snapshotTargetWords.size(), consumeBag, sourceMaterialId);
        return aiAsyncTaskService.toDetailResponse(created);
    }

    /**
     * 查询指定单词本和场景下最新一条知识反刍材料异步任务，用于前端刷新恢复状态。
     *
     * @param userId       当前用户 ID
     * @param wordbookId   当前单词本 ID
     * @param scenarioCode 场景编码
     * @return 最新任务详情，未提交过时返回 {@code null}
     */
    public AiAsyncTaskResponse findLatestTaskForScenario(Long userId, Long wordbookId, String scenarioCode) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, wordbookId);
        String normalizedCode = scenarioCode == null ? "" : scenarioCode.trim();
        if (!StringUtils.hasText(normalizedCode)) {
            return null;
        }
        AiAsyncTask task = aiAsyncTaskService.findLatestByJobAndScenario(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, wordbook.getId(), normalizedCode);
        return task == null ? null : aiAsyncTaskService.toDetailResponse(task);
    }

    private boolean isActiveTask(AiAsyncTask task) {
        if (task == null) {
            return false;
        }
        return List.of(
                AiTaskConstants.STATUS_PENDING,
                AiTaskConstants.STATUS_RUNNING,
                AiTaskConstants.STATUS_RETRY_WAIT).contains(task.getStatus());
    }

    private boolean matchesActiveDuplicate(AiAsyncTask task, String expectedSignature,
                                           Long sourceMaterialId, boolean consumeBag) {
        if (task == null || !StringUtils.hasText(task.getPayloadJson()) || !StringUtils.hasText(expectedSignature)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(task.getPayloadJson());
            boolean taskConsumeBag = !node.has("consumeBag") || node.path("consumeBag").asBoolean(true);
            if (consumeBag != taskConsumeBag) {
                return false;
            }
            if (sourceMaterialId != null && node.hasNonNull("sourceMaterialId")
                    && String.valueOf(sourceMaterialId).equals(node.path("sourceMaterialId").asText(""))) {
                return true;
            }
            return expectedSignature.equals(node.path("contentSignature").asText(null));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeOptional(String raw, String defaultValue) {
        return StringUtils.hasText(raw) ? raw.trim().toLowerCase(Locale.ROOT) : defaultValue;
    }

    private String buildRequestIdempotencyKey(Long userId, String requestId) {
        String raw = "rum_mat_req:" + userId + ":" + requestId.trim();
        return raw.length() <= 160 ? raw : "rum_mat_req:" + userId + ":" + sha256Hex(requestId.trim(), 32);
    }

    private String buildContentSignature(Long wordbookId, String scenarioCode, String difficulty,
                                         String wordCountRange, List<String> targetWords) {
        List<String> sortedWords = targetWords.stream().sorted().toList();
        String canonical = wordbookId + "|" + scenarioCode + "|" + difficulty + "|"
                + wordCountRange + "|" + String.join(",", sortedWords);
        return sha256Hex(canonical, 32);
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
