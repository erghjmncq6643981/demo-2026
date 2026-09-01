package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterial;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneRelatedWord;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneRelatedWordMapper;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 为已经发布的场景材料分批补充相关词汇；每批独立落库，失败重试只补未完成数量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningSceneRelatedVocabularyService {

    public static final int DEFAULT_TARGET_COUNT = 50;
    public static final int MAX_TARGET_COUNT = 100;
    private static final int BATCH_SIZE = 25;

    private final LearningPlanAccessService planAccessService;
    private final LearningSceneMaterialMapper materialMapper;
    private final LearningPlanUnitEntryMapper entryMapper;
    private final LearningSceneRelatedWordMapper relatedWordMapper;
    private final AiChatService aiChatService;
    private final TransactionTemplate transactionTemplate;

    /** 生成到指定目标数量，已存在结果视为检查点并自动跳过。 */
    public int generate(Long userId, Long planId, Long unitId, Long modelConfigId, Integer targetCount) {
        LearningPlan plan = planAccessService.requireOwnedPlan(userId, planId);
        LearningPlanUnit unit = planAccessService.requireUnit(plan, unitId);
        LearningSceneMaterial material = requireCurrentMaterial(userId, planId, unit);
        int target = Math.max(1, Math.min(targetCount == null ? DEFAULT_TARGET_COUNT : targetCount,
                MAX_TARGET_COUNT));
        int current = count(material.getId());
        int consecutiveZeroBatches = 0;
        while (current < target) {
            int batchTarget = Math.min(BATCH_SIZE, target - current);
            int inserted = generateBatch(plan, unit, material, modelConfigId, batchTarget);
            if (inserted == CommonConstants.ZERO) {
                consecutiveZeroBatches++;
                if (consecutiveZeroBatches >= 2) {
                    if (current > 0) {
                        log.warn("相关词汇生成部分完成: unitId={} materialId={} current={} target={}",
                                unit.getId(), material.getId(), current, target);
                        break;
                    }
                    throw LearningAssistantException.badRequest(
                            LearningErrorCode.LEARNING_SCENE_PARSE_FAILED,
                            "AI 未返回可保存的场景相关词汇，可继续任务重试当前批次");
                }
            } else {
                consecutiveZeroBatches = 0;
                current += inserted;
            }
        }
        return current;
    }

    /** 统计当前场景材料的相关词数量。 */
    public int count(Long materialId) {
        return Math.toIntExact(relatedWordMapper.selectCount(new LambdaQueryWrapper<LearningSceneRelatedWord>()
                .eq(LearningSceneRelatedWord::getSceneMaterialId, materialId)
                .eq(LearningSceneRelatedWord::getDeleted, false)));
    }

    private int generateBatch(LearningPlan plan, LearningPlanUnit unit, LearningSceneMaterial material,
                              Long modelConfigId, int targetCount) {
        List<LearningPlanUnitEntry> coreEntries = entryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                        .eq(LearningPlanUnitEntry::getTier, ScenePlanConstants.TIER_CORE)
                        .eq(LearningPlanUnitEntry::getDeleted, false)
                        .orderByAsc(LearningPlanUnitEntry::getSortOrder));
        List<LearningSceneRelatedWord> existing = relatedWordMapper.selectList(
                new LambdaQueryWrapper<LearningSceneRelatedWord>()
                        .eq(LearningSceneRelatedWord::getSceneMaterialId, material.getId())
                        .eq(LearningSceneRelatedWord::getDeleted, false));
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("learning_purpose", plan.getLearningPurpose());
        variables.put("scene_title", unit.getTitle());
        variables.put("scene_summary", unit.getSummary());
        variables.put("learning_text", material.getLearningText());
        variables.put("core_words", coreEntries.stream().map(LearningPlanUnitEntry::getTerm).toList());
        variables.put("existing_words", existing.stream().map(LearningSceneRelatedWord::getTerm).toList());
        variables.put("target_word_count", targetCount);

        AgentChatRequest request = new AgentChatRequest();
        request.setUserId(plan.getUserId());
        request.setInvocationScene(AiInvocationScene.VOCABULARY_SCENE_RELATED_WORDS);
        request.setAgentCode(AiScenarioConstants.VOCABULARY_PLAN_AGENT_CODE);
        request.setTemplateCode(AiScenarioConstants.VOCABULARY_SCENE_RELATED_TEMPLATE_CODE);
        // 场景相关词是独立动作，保留审计关联但不带入长期会话历史。
        request.setSessionId(null);
        request.setTitle(LearningScene.ENGLISH_VOCABULARY_PLAN.getTitle());
        request.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        request.setBusinessId(String.valueOf(material.getId()));
        request.setSceneCode(LearningScene.ENGLISH_VOCABULARY_PLAN.getCode());
        request.setModelConfigId(modelConfigId);
        request.setMessage("为场景材料“" + unit.getTitle() + "”补充一批不重复的场景相关词汇。");
        request.setVariables(variables);
        AgentChatResponse response = aiChatService.chat(request);
        JsonNode root = response.requireStructuredRoot(AiInvocationScene.VOCABULARY_SCENE_RELATED_WORDS);
        JsonNode words = extractWordsNode(root);
        if (words == null || !words.isArray()) return CommonConstants.ZERO;

        Set<String> excluded = new HashSet<>();
        coreEntries.stream().map(LearningPlanUnitEntry::getNormalizedTerm).filter(StringUtils::hasText)
                .forEach(excluded::add);
        existing.stream().map(LearningSceneRelatedWord::getNormalizedTerm).filter(StringUtils::hasText)
                .forEach(excluded::add);
        List<LearningSceneRelatedWord> batch = new ArrayList<>();
        int sortOrder = existing.size() + 1;
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode word : words) {
            if (batch.size() >= targetCount) break;
            String term = text(word, "term", "word", "vocabulary", "english", "name");
            String normalized = normalize(term);
            if (!StringUtils.hasText(normalized) || !excluded.add(normalized)) continue;
            String categoryCode = text(word, "category_code", "categoryCode", "category", "type");
            String categoryName = text(word, "category_name", "categoryName", "categoryLabel", "group");
            LearningSceneRelatedWord entity = new LearningSceneRelatedWord();
            entity.setId(IdWorker.getId());
            entity.setUserId(plan.getUserId());
            entity.setPlanId(plan.getId());
            entity.setUnitId(unit.getId());
            entity.setSceneMaterialId(material.getId());
            entity.setTerm(term.trim());
            entity.setNormalizedTerm(normalized);
            entity.setPhonetic(text(word, "phonetic", "ipa"));
            entity.setMeaningText(text(word, "meaning", "definition", "chinese", "translation"));
            entity.setContextMeaning(text(word, "context_meaning", "contextMeaning", "usage", "context"));
            entity.setCategoryCode(StringUtils.hasText(categoryCode) ? categoryCode : "context");
            entity.setCategoryName(StringUtils.hasText(categoryName) ? categoryName : "情景拓展");
            entity.setSourceType("ai");
            entity.setSortOrder(sortOrder++);
            entity.setPromoted(false);
            entity.setCreateBy(plan.getUserId());
            entity.setUpdateBy(plan.getUserId());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            entity.setDeleted(false);
            entity.setVersion(CommonConstants.ZERO);
            batch.add(entity);
        }
        if (batch.isEmpty()) return CommonConstants.ZERO;
        Objects.requireNonNull(transactionTemplate.execute(status -> relatedWordMapper.insertBatch(batch)));
        log.info("用户场景相关词汇批次已保存 userId={} planId={} unitId={} materialId={} count={}",
                plan.getUserId(), plan.getId(), unit.getId(), material.getId(), batch.size());
        return batch.size();
    }

    private JsonNode extractWordsNode(JsonNode root) {
        if (root == null) return null;
        if (root.has("related_words") && root.get("related_words").isArray()) {
            return root.get("related_words");
        }
        if (root.has("relatedWords") && root.get("relatedWords").isArray()) {
            return root.get("relatedWords");
        }
        if (root.has("words") && root.get("words").isArray()) {
            return root.get("words");
        }
        if (root.has("vocabulary") && root.get("vocabulary").isArray()) {
            return root.get("vocabulary");
        }
        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }
        if (root.isArray()) {
            return root;
        }
        return null;
    }

    private LearningSceneMaterial requireCurrentMaterial(Long userId, Long planId, LearningPlanUnit unit) {
        LearningSceneMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<LearningSceneMaterial>()
                .eq(LearningSceneMaterial::getId, unit.getSceneMaterialId())
                .eq(LearningSceneMaterial::getUserId, userId)
                .eq(LearningSceneMaterial::getPlanId, planId)
                .eq(LearningSceneMaterial::getUnitId, unit.getId())
                .eq(LearningSceneMaterial::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (material == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND);
        }
        return material;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
