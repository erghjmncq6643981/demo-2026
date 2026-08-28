package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterial;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogQueryService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 将已完成 AI 校验的场景内容写入学习单元、材料版本和单词本。
 * 该服务只应在调用方建立的短事务中执行，绝不负责发起 AI 请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanScenePersistenceService {

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final VocabularyCatalogQueryService catalogQueryService;
    private final LearningWordProgressService progressService;
    private final WordbookService wordbookService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final LearningPlanResponseAssembler responseAssembler;
    private final LearningPlanAssessmentSupport assessmentSupport;
    private final ObjectMapper objectMapper;

    /** 重新生成内容并发布新材料版本，保留旧版本以便历史追溯。 */
    public LearningPlanUnitResponse switchMaterialVersion(Long userId, LearningPlanUnit unit,
                                                           List<LearningPlanUnitEntry> entries,
                                                           AgentChatResponse aiResponse, JsonNode scene,
                                                           List<JsonNode> words) {
        LocalDateTime now = LocalDateTime.now();
        LearningSceneMaterial previous = materialMapper.selectById(unit.getSceneMaterialId());
        int revision = previous == null || previous.getRevisionNo() == null
                ? CommonConstants.FIRST_SEQUENCE : previous.getRevisionNo() + 1;
        if (previous != null) {
            previous.setCurrentVersion(false);
            previous.setMaterialStatus("archived");
            previous.setUpdateTime(now);
            materialMapper.updateById(previous);
        }
        LearningSceneMaterial material = new LearningSceneMaterial();
        material.setUserId(userId);
        material.setPlanId(unit.getPlanId());
        material.setUnitId(unit.getId());
        material.setRevisionNo(revision);
        material.setMaterialStatus("published");
        material.setCurrentVersion(true);
        material.setSupersedesMaterialId(previous == null ? null : previous.getId());
        material.setSessionId(aiResponse.getSessionId());
        material.setTitle(requiredText(scene, "title"));
        material.setScenarioType(text(scene, "scenario_type", "scenarioType"));
        material.setLearningText(text(scene, "learning_text", "learningText", "article"));
        material.setTranslation(text(scene, "translation"));
        material.setRawContent(aiResponse.getContent());
        material.setParsedJson(writeJson(scene));
        material.setProvider(aiResponse.getModelProvider());
        material.setModelName(aiResponse.getModelName());
        material.setTokenUsage(aiResponse.getTokenUsage());
        material.setCostTime(aiResponse.getCostTime());
        material.setDeleted(false);
        material.setCreateTime(now);
        material.setUpdateTime(now);
        materialMapper.insert(material);

        Map<String, JsonNode> generatedByTerm = words.stream().collect(Collectors.toMap(
                word -> normalize(requiredText(word, "term", "word")), word -> word,
                (left, right) -> left, LinkedHashMap::new));
        List<LearningPlanUnitEntry> updatedEntries = new ArrayList<>();
        for (LearningPlanUnitEntry entry : entries) {
            JsonNode generated = generatedByTerm.get(entry.getNormalizedTerm());
            if (generated == null) continue;
            entry.setPhonetic(firstText(text(generated, "phonetic"), entry.getPhonetic()));
            entry.setMeaningText(firstText(text(generated, "meaning", "definition"), entry.getMeaningText()));
            entry.setContextMeaning(firstText(text(generated, "context_meaning", "contextMeaning"),
                    entry.getContextMeaning()));
            JsonNode question = node(generated, "meaning_question", "meaningQuestion", "assessment");
            if (ScenePlanConstants.TIER_CORE.equals(entry.getTier())
                    || ScenePlanConstants.TIER_REVIEW.equals(entry.getTier())) {
                question = assessmentSupport.ensureMeaningQuestion(generated, entry.getTerm(), question,
                        entry.getMeaningText());
            }
            entry.setAssessmentJson(question == null ? entry.getAssessmentJson() : writeJson(question));
            entry.setAcceptedSpellingsJson(writeJson(assessmentSupport.acceptedSpellings(generated, entry.getTerm())));
            entry.setUpdateBy(userId);
            entry.setUpdateTime(now);
            updatedEntries.add(entry);
        }
        if (!updatedEntries.isEmpty()) unitEntryMapper.updateBatch(updatedEntries);
        unit.setTitle(material.getTitle());
        unit.setScenarioType(material.getScenarioType());
        unit.setSummary(text(scene, "summary", "description"));
        unit.setSceneMaterialId(material.getId());
        unit.setGeneratedTime(now);
        unit.setSupplementaryWordCount(CommonConstants.ZERO);
        unit.setUpdateTime(now);
        unitMapper.updateById(unit);
        return responseAssembler.toUnitResponse(unit);
    }

    /** 持久化新生成的场景、词条、进度和个人单词本快照。 */
    public LearningPlanUnitResponse persistGeneratedUnit(Long userId, LearningPlan plan,
                                                          LocalDate resolvedRecommendedDate, LocalDate today,
                                                          List<VocabularyCatalogEntry> candidates,
                                                          List<VocabularyCatalogEntry> reviewWords, int unitNo,
                                                          AgentChatResponse aiResponse, JsonNode scene,
                                                          List<JsonNode> words) {
        LocalDateTime now = LocalDateTime.now();
        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setPlanId(plan.getId());
        unit.setUnitNo(unitNo);
        unit.setTitle(requiredText(scene, "title"));
        unit.setScenarioType(text(scene, "scenario_type", "scenarioType"));
        unit.setSummary(text(scene, "summary", "description"));
        boolean startImmediately = plan.getCurrentUnitId() == null && !resolvedRecommendedDate.isAfter(today);
        unit.setStatus(startImmediately ? ScenePlanConstants.UNIT_IN_PROGRESS
                : ScenePlanConstants.UNIT_READY);
        unit.setCoreWordCount(CommonConstants.ZERO);
        unit.setExtendedWordCount(CommonConstants.ZERO);
        unit.setSupplementaryWordCount(CommonConstants.ZERO);
        unit.setCompletedCoreCount(CommonConstants.ZERO);
        unit.setGeneratedTime(now);
        unit.setStartedTime(startImmediately ? now : null);
        unit.setRecommendedDate(resolvedRecommendedDate);
        unit.setDeleted(false);
        unit.setCreateTime(now);
        unit.setUpdateTime(now);
        unitMapper.insert(unit);

        LearningSceneMaterial material = new LearningSceneMaterial();
        material.setUserId(userId);
        material.setPlanId(plan.getId());
        material.setUnitId(unit.getId());
        material.setRevisionNo(CommonConstants.FIRST_SEQUENCE);
        material.setMaterialStatus("published");
        material.setCurrentVersion(true);
        material.setSessionId(aiResponse.getSessionId());
        material.setTitle(unit.getTitle());
        material.setScenarioType(unit.getScenarioType());
        material.setLearningText(text(scene, "learning_text", "learningText", "article"));
        material.setTranslation(text(scene, "translation"));
        material.setRawContent(aiResponse.getContent());
        material.setParsedJson(writeJson(scene));
        material.setProvider(aiResponse.getModelProvider());
        material.setModelName(aiResponse.getModelName());
        material.setTokenUsage(aiResponse.getTokenUsage());
        material.setCostTime(aiResponse.getCostTime());
        material.setDeleted(false);
        material.setCreateTime(now);
        material.setUpdateTime(now);
        materialMapper.insert(material);

        Map<String, VocabularyCatalogEntry> candidateMap = Stream.concat(candidates.stream(), reviewWords.stream())
                .collect(Collectors.toMap(entry -> normalize(entry.effectiveTerm()), entry -> entry,
                        (left, right) -> left, LinkedHashMap::new));
        Set<String> missingTerms = new LinkedHashSet<>();
        for (JsonNode word : words) {
            String normalized = normalize(requiredText(word, "term", "word"));
            if (!candidateMap.containsKey(normalized)) missingTerms.add(normalized);
        }
        if (!missingTerms.isEmpty()) {
            for (VocabularyCatalogEntry entry : catalogQueryService.findByNormalizedTerms(
                    plan.getCatalogVersionId(), missingTerms)) {
                candidateMap.put(entry.getNormalizedTerm(), entry);
            }
        }

        List<LearningWordProgressService.SceneExposureCommand> progressCommands = words.stream()
                .map(word -> new LearningWordProgressService.SceneExposureCommand(
                        requiredText(word, "term", "word"),
                        normalizeRequirement(text(word, "mastery_requirement", "masteryRequirement")),
                        normalizeTier(text(word, "tier")), plan.getId(), unit.getId()))
                .toList();
        LearningWordProgressService.SceneProgressBatch progressBatch = progressService.prepareSceneProgresses(
                userId, progressCommands, startImmediately);
        List<PreparedSceneWord> preparedWords = new ArrayList<>(words.size());
        for (int i = 0; i < words.size(); i++) {
            JsonNode word = words.get(i);
            LearningWordProgressService.SceneExposureCommand command = progressCommands.get(i);
            String normalizedTerm = normalize(command.term());
            LearningWordProgress progress = progressBatch.progresses().get(normalizedTerm);
            preparedWords.add(new PreparedSceneWord(word, command.term(), normalizedTerm,
                    candidateMap.get(normalizedTerm), command.tier(), command.masteryRequirement(), progress,
                    ScenePlanConstants.TIER_CORE.equals(command.tier())
                            && progressBatch.initiallyUnseenTerms().contains(normalizedTerm)));
        }
        List<WordbookService.LearningEntryCommand> wordbookCommands = preparedWords.stream()
                .filter(prepared -> !ScenePlanConstants.TIER_SUPPLEMENTARY.equals(prepared.tier()))
                .map(prepared -> new WordbookService.LearningEntryCommand(prepared.source(), prepared.progress(),
                        prepared.term(), prepared.normalizedTerm(),
                        ScenePlanConstants.TIER_CORE.equals(prepared.tier())
                                || ScenePlanConstants.TIER_REVIEW.equals(prepared.tier())))
                .toList();
        Map<String, LearningWordbookEntry> wordbookEntries = wordbookService.ensureLearningEntries(
                userId, plan.getWordbookId(), wordbookCommands, now);

        List<LearningPlanUnitEntry> unitEntries = new ArrayList<>(words.size());
        int coreCount = 0, extendedCount = 0, supplementaryCount = 0, sortOrder = CommonConstants.FIRST_SEQUENCE;
        for (PreparedSceneWord prepared : preparedWords) {
            JsonNode word = prepared.word();
            String term = prepared.term();
            VocabularyCatalogEntry source = prepared.source();
            String tier = prepared.tier();
            LearningWordbookEntry wordbookEntry = wordbookEntries.get(prepared.normalizedTerm());
            String fallbackMeaning = firstText(text(word, "meaning", "definition"),
                    source == null ? null : source.getDefinitionText());
            JsonNode question = node(word, "meaning_question", "meaningQuestion", "assessment");
            if (ScenePlanConstants.TIER_CORE.equals(tier)
                    || ScenePlanConstants.TIER_REVIEW.equals(tier)) {
                question = assessmentSupport.ensureMeaningQuestion(word, term, question, fallbackMeaning);
            }
            LearningPlanUnitEntry entry = new LearningPlanUnitEntry();
            entry.setId(IdWorker.getId());
            entry.setCreateBy(userId);
            entry.setUpdateBy(userId);
            entry.setPlanId(plan.getId());
            entry.setUnitId(unit.getId());
            entry.setCatalogEntryId(source == null ? null : source.getId());
            entry.setWordbookEntryId(wordbookEntry == null ? null : wordbookEntry.getId());
            entry.setWordProgressId(prepared.progress().getId());
            entry.setSourceOrder(source == null ? null : source.getSourceOrder());
            entry.setTerm(source == null ? term : source.effectiveTerm());
            entry.setNormalizedTerm(source == null ? prepared.normalizedTerm() : source.getNormalizedTerm());
            entry.setPhonetic(firstText(text(word, "phonetic"), source == null ? null : source.getPhonetic()));
            entry.setMeaningText(fallbackMeaning);
            entry.setContextMeaning(text(word, "context_meaning", "contextMeaning"));
            entry.setTier(tier);
            entry.setMasteryRequirement(prepared.requirement());
            entry.setAcceptedSpellingsJson(writeJson(assessmentSupport.acceptedSpellings(word, term)));
            entry.setAssessmentJson(question == null || question.isMissingNode() ? null : writeJson(question));
            entry.setFirstLearning(prepared.firstLearning());
            entry.setSortOrder(sortOrder++);
            entry.setDeleted(false);
            entry.setVersion(CommonConstants.ZERO);
            entry.setCreateTime(now);
            entry.setUpdateTime(now);
            unitEntries.add(entry);
            if (ScenePlanConstants.TIER_CORE.equals(tier)) coreCount++;
            else if (ScenePlanConstants.TIER_SUPPLEMENTARY.equals(tier)) supplementaryCount++;
            else extendedCount++;
        }
        if (!unitEntries.isEmpty()) unitEntryMapper.insertBatch(unitEntries);
        unit.setCoreWordCount(coreCount);
        unit.setExtendedWordCount(extendedCount);
        unit.setSupplementaryWordCount(supplementaryCount);
        unit.setSceneMaterialId(material.getId());
        unit.setUpdateTime(now);
        unitMapper.updateById(unit);
        if (startImmediately) plan.setCurrentUnitId(unit.getId());
        plan.setAiSessionId(aiResponse.getSessionId());
        plan.setUpdateTime(now);
        planMapper.updateById(plan);
        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "生成场景学习单元",
                plan.getName() + " / " + unit.getTitle() + "，核心词 " + coreCount + " 个");
        log.info("用户「{}」为计划「{}」生成了第 {} 个场景「{}」，核心词 {} 个、扩展词 {} 个、补充词 {} 个",
                userDisplayNameService.userName(userId), plan.getName(), unitNo, unit.getTitle(),
                coreCount, extendedCount, supplementaryCount);
        return responseAssembler.toUnitResponse(unit);
    }

    private String normalizeTier(String tier) {
        if (ScenePlanConstants.TIER_CORE.equals(tier)
                || ScenePlanConstants.TIER_EXTENDED.equals(tier)
                || ScenePlanConstants.TIER_SUPPLEMENTARY.equals(tier)
                || ScenePlanConstants.TIER_REVIEW.equals(tier)) return tier;
        return ScenePlanConstants.TIER_EXTENDED;
    }

    private String normalizeRequirement(String requirement) {
        return ScenePlanConstants.MASTERY_SPELLING.equals(requirement)
                ? ScenePlanConstants.MASTERY_SPELLING : ScenePlanConstants.MASTERY_RECOGNITION;
    }

    private JsonNode node(JsonNode source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            JsonNode value = source.path(key);
            if (!value.isMissingNode() && !value.isNull()) return value;
        }
        return null;
    }

    private String requiredText(JsonNode source, String... keys) {
        String value = text(source, keys);
        if (!StringUtils.hasText(value)) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_PARSE_FAILED,
                    "AI 场景结果缺少字段: " + String.join("/", keys));
        }
        return value;
    }

    private String text(JsonNode source, String... keys) {
        JsonNode value = node(source, keys);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim() : null;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "场景学习数据序列化失败", ex);
        }
    }

    private record PreparedSceneWord(JsonNode word, String term, String normalizedTerm,
                                     VocabularyCatalogEntry source, String tier, String requirement,
                                     LearningWordProgress progress, boolean firstLearning) {
    }
}
