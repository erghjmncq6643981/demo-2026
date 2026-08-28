package com.chandler.learning.agent.learning.application;

import cn.hutool.core.util.StrUtil;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责学习计划场景内容的 AI 请求、结果解析和词条边界校验。
 *
 * <p>该服务不持有数据库事务。调用方在校验通过后再进入短事务持久化场景材料，
 * 从而避免把模型响应耗时包含在数据库事务中。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanSceneContentService {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    /** 将词表候选词转换为模型输入并发起场景生成请求。 */
    public AgentChatResponse generateScene(LearningPlan plan, int unitNo,
                                           List<VocabularyCatalogEntry> candidates,
                                           List<VocabularyCatalogEntry> reviewWords,
                                           int targetWordCount, Long modelConfigId) {
        List<SceneCandidate> words = candidates.stream()
                .map(entry -> new SceneCandidate(entry.effectiveTerm(),
                        entry.getPhonetic(), entry.getDefinitionText()))
                .toList();
        List<SceneCandidate> review = reviewWords.stream()
                .map(entry -> new SceneCandidate(entry.effectiveTerm(),
                        entry.getPhonetic(), entry.getDefinitionText()))
                .toList();
        return generateSceneWithWords(plan, unitNo, words, review, targetWordCount, modelConfigId);
    }

    /** 使用固定核心词和复习词重新生成同一场景的内容。 */
    public AgentChatResponse generateSceneWithWords(LearningPlan plan, int unitNo,
                                                    List<SceneCandidate> words,
                                                    List<SceneCandidate> review,
                                                    int targetWordCount, Long modelConfigId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("learning_purpose", StrUtil.blankToDefault(plan.getLearningPurpose(), "综合英语词汇学习"));
        variables.put("unit_no", unitNo);
        variables.put("candidate_words", words);
        variables.put("review_words", review);
        variables.put("target_word_count", targetWordCount);

        AgentChatRequest request = new AgentChatRequest();
        request.setUserId(plan.getUserId());
        request.setInvocationScene(AiInvocationScene.VOCABULARY_SCENE_UNIT);
        request.setAgentCode(AiScenarioConstants.VOCABULARY_PLAN_AGENT_CODE);
        request.setTemplateCode(AiScenarioConstants.VOCABULARY_PLAN_TEMPLATE_CODE);
        request.setSessionId(plan.getAiSessionId());
        request.setTitle(LearningScene.ENGLISH_VOCABULARY_PLAN.getTitle());
        request.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        request.setBusinessId(LearningScene.ENGLISH_VOCABULARY_PLAN.getCode());
        request.setSceneCode(LearningScene.ENGLISH_VOCABULARY_PLAN.getCode());
        request.setModelConfigId(modelConfigId);
        request.setMessage("请为学习计划“" + plan.getName() + "”生成第 " + unitNo + " 个场景单元。");
        request.setVariables(variables);
        return aiChatService.chat(request);
    }

    /** 校验 AI 返回的核心词、复习词必须来自本次输入集合。 */
    public List<JsonNode> validateSceneWords(JsonNode scene, List<VocabularyCatalogEntry> candidates,
                                             List<VocabularyCatalogEntry> reviewWords, int targetWordCount) {
        return validateSceneWords(scene,
                candidates.stream().map(VocabularyCatalogEntry::effectiveTerm).collect(Collectors.toSet()),
                reviewWords.stream().map(VocabularyCatalogEntry::effectiveTerm).collect(Collectors.toSet()),
                targetWordCount);
    }

    /**
     * 校验词条来源、去重和核心词数量。允许 AI 返回的词条带常见屈折变化，
     * 但不会接受计划候选集之外的词，避免污染学习计划。
     */
    public List<JsonNode> validateSceneWords(JsonNode scene, Set<String> candidateInputTerms,
                                             Set<String> reviewInputTerms, int targetWordCount) {
        JsonNode vocabulary = node(scene, "vocabulary", "words");
        if (vocabulary == null || !vocabulary.isArray() || vocabulary.isEmpty()) {
            throw sceneInvalid("AI 场景结果缺少 vocabulary 数组");
        }
        int coreCount = CommonConstants.ZERO;
        List<JsonNode> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> candidateTerms = candidateInputTerms.stream().map(this::normalize).collect(Collectors.toSet());
        Set<String> reviewTerms = reviewInputTerms.stream().map(this::normalize).collect(Collectors.toSet());
        for (JsonNode word : vocabulary) {
            String term = requiredText(word, "term", "word");
            String normalized = normalize(term);
            String matchedCandidate = resolveCandidateLemma(normalized, candidateTerms);
            String matchedReview = matchedCandidate == null ? resolveCandidateLemma(normalized, reviewTerms) : null;
            String canonicalTerm = matchedCandidate != null ? matchedCandidate : (matchedReview != null ? matchedReview : normalized);
            if (!seen.add(canonicalTerm)) {
                continue;
            }
            if (matchedCandidate != null) {
                if (word instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
                    obj.put("term", matchedCandidate);
                    obj.put("tier", ScenePlanConstants.TIER_CORE);
                }
                coreCount++;
                result.add(word);
            } else if (matchedReview != null) {
                if (word instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
                    obj.put("term", matchedReview);
                    obj.put("tier", ScenePlanConstants.TIER_REVIEW);
                }
                result.add(word);
            } else {
                log.info("AI 场景生成返回了不在计划候选集或复习集的额外词条，已自动过滤: term={}", term);
            }
        }
        int requiredMinimum = Math.min(targetWordCount, candidateTerms.size());
        int acceptableMinimum = Math.min(requiredMinimum,
                Math.max(1, (int) Math.floor(requiredMinimum * 0.75)));
        if (coreCount < acceptableMinimum) {
            throw sceneInvalid("核心词数量不足，期望至少 " + acceptableMinimum + " 个，实际为 " + coreCount + " 个");
        }
        if (coreCount > ScenePlanConstants.MAX_CORE_WORDS_PER_UNIT) {
            throw sceneInvalid("单篇场景材料最多包含 "
                    + ScenePlanConstants.MAX_CORE_WORDS_PER_UNIT + " 个待挑战词，实际为 " + coreCount + " 个");
        }
        return result;
    }

    private String resolveCandidateLemma(String normalized, Set<String> candidates) {
        if (candidates.contains(normalized)) {
            return normalized;
        }
        // 尝试常见屈折变形还原：activities -> activity
        if (normalized.endsWith("ies") && normalized.length() > 3) {
            String candidate = normalized.substring(0, normalized.length() - 3) + "y";
            if (candidates.contains(candidate)) {
                return candidate;
            }
        }
        // utilizes -> utilize，actresses -> actress
        if (normalized.endsWith("es") && normalized.length() > 3) {
            String candidateNoS = normalized.substring(0, normalized.length() - 1);
            if (candidates.contains(candidateNoS)) {
                return candidateNoS;
            }
            String candidateNoEs = normalized.substring(0, normalized.length() - 2);
            if (candidates.contains(candidateNoEs)) {
                return candidateNoEs;
            }
        }
        // utilized -> utilize，annoyed -> annoy
        if (normalized.endsWith("ed") && normalized.length() > 3) {
            String candidateE = normalized.substring(0, normalized.length() - 1);
            if (candidates.contains(candidateE)) {
                return candidateE;
            }
            String candidateNoEd = normalized.substring(0, normalized.length() - 2);
            if (candidates.contains(candidateNoEd)) {
                return candidateNoEd;
            }
        }
        // utilizing -> utilize，annoying -> annoy
        if (normalized.endsWith("ing") && normalized.length() > 4) {
            String candidateE = normalized.substring(0, normalized.length() - 3) + "e";
            if (candidates.contains(candidateE)) {
                return candidateE;
            }
            String candidateNoIng = normalized.substring(0, normalized.length() - 3);
            if (candidates.contains(candidateNoIng)) {
                return candidateNoIng;
            }
        }
        // artists -> artist，winds -> wind
        if (normalized.endsWith("s") && normalized.length() > 2) {
            String candidateNoS = normalized.substring(0, normalized.length() - 1);
            if (candidates.contains(candidateNoS)) {
                return candidateNoS;
            }
        }
        return null;
    }

    private JsonNode node(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String requiredText(JsonNode node, String... keys) {
        String value = text(node, keys);
        if (!StringUtils.hasText(value)) {
            throw sceneInvalid("AI 场景结果缺少字段: " + String.join("/", keys));
        }
        return value;
    }

    private String text(JsonNode node, String... keys) {
        JsonNode value = node(node, keys);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private LearningAssistantException sceneInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningErrorCode.LEARNING_SCENE_PARSE_FAILED, message);
    }

    /** 模型候选词的精简传输对象，避免把词表实体和内部字段传给模型。 */
    public record SceneCandidate(String term, String phonetic, String meaning) {
    }
}
