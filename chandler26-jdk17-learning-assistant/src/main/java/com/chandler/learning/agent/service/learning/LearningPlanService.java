package com.chandler.learning.agent.service.learning;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanCreateRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUnitResponse;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.ReviewSubmitResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningPlan;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnit;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import com.chandler.learning.agent.domain.entity.learning.LearningReviewRecord;
import com.chandler.learning.agent.domain.entity.learning.LearningSceneMaterial;
import com.chandler.learning.agent.domain.entity.learning.LearningWordProgress;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbook;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalog;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogVersion;
import com.chandler.learning.agent.domain.enums.LearningScene;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.LearningPlanMapper;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitMapper;
import com.chandler.learning.agent.mapper.learning.LearningReviewRecordMapper;
import com.chandler.learning.agent.mapper.learning.LearningSceneMaterialMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordProgressMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogVersionMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 按需生成并推进场景学习单元，不设置每日场景或拼写词数量限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final LearningWordProgressMapper progressMapper;
    private final LearningReviewRecordMapper reviewRecordMapper;
    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper wordbookEntryMapper;
    private final VocabularyCatalogMapper catalogMapper;
    private final VocabularyCatalogVersionMapper catalogVersionMapper;
    private final VocabularyCatalogEntryMapper catalogEntryMapper;
    private final LearningWordProgressService progressService;
    private final WordbookService wordbookService;
    private final AiChatService aiChatService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;

    /**
     * 创建自助学习计划；默认立即生成第一个场景单元。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse create(Long userId, LearningPlanCreateRequest request) {
        VocabularyCatalogVersion version = requirePublishedVersion(userId, request.getCatalogVersionId());
        VocabularyCatalog catalog = requireCatalog(userId, version.getCatalogId());
        Long wordbookId = request.getWordbookId() == null
                ? wordbookService.ensureDefaultWordbook(userId).getId()
                : requireWordbook(userId, request.getWordbookId()).getId();
        int totalWords = catalogEntryMapper.selectCount(new LambdaQueryWrapper<VocabularyCatalogEntry>()
                .eq(VocabularyCatalogEntry::getCatalogVersionId, version.getId())
                .eq(VocabularyCatalogEntry::getPublished, true)
                .eq(VocabularyCatalogEntry::getDeleted, false)).intValue();
        if (totalWords == LearningConstants.ZERO) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_NO_WORDS,
                    "已发布词表中没有可学习词汇");
        }

        LocalDateTime now = LocalDateTime.now();
        LearningPlan plan = new LearningPlan();
        plan.setUserId(userId);
        plan.setCatalogId(catalog.getId());
        plan.setCatalogVersionId(version.getId());
        plan.setWordbookId(wordbookId);
        plan.setName(request.getName().trim());
        plan.setLearningPurpose(StringUtils.hasText(request.getLearningPurpose())
                ? request.getLearningPurpose().trim()
                : catalog.getLearningPurpose());
        plan.setStatus(LearningConstants.ScenePlan.STATUS_ACTIVE);
        plan.setTotalCatalogWords(totalWords);
        plan.setLearnedCoreWords(LearningConstants.ZERO);
        plan.setCompletedUnitCount(LearningConstants.ZERO);
        plan.setDeleted(false);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        planMapper.insert(plan);

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "创建场景学习计划",
                plan.getName() + "，词表共 " + totalWords + " 词");
        log.info("用户「{}」基于词表「{}」创建了场景学习计划「{}」，共 {} 个词",
                userDisplayNameService.userName(userId), catalog.getName(), plan.getName(), totalWords);
        if (!Boolean.FALSE.equals(request.getGenerateFirstUnit())) {
            generateNextUnit(userId, plan.getId(), request.getModelConfigId());
        }
        return detail(userId, plan.getId());
    }

    public List<LearningPlanResponse> list(Long userId) {
        return planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId)
                        .eq(LearningPlan::getDeleted, false)
                        .orderByDesc(LearningPlan::getUpdateTime))
                .stream()
                .map(plan -> toPlanResponse(plan, false))
                .toList();
    }

    public LearningPlanResponse detail(Long userId, Long planId) {
        return toPlanResponse(requirePlan(userId, planId), true);
    }

    /**
     * 在当前单元完成后手动触发下一个单元。每天可重复执行，不做配额限制。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanUnitResponse generateNextUnit(Long userId, Long planId, Long modelConfigId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_COMPLETED,
                    "学习计划已经完成");
        }
        LearningPlanUnit active = findActiveUnit(plan.getId());
        if (active != null) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_UNIT_ACTIVE,
                    "请先完成当前场景“" + active.getTitle() + "”再生成下一个");
        }

        List<VocabularyCatalogEntry> candidates = nextCandidates(plan);
        if (candidates.isEmpty()) {
            plan.setStatus(LearningConstants.ScenePlan.STATUS_COMPLETED);
            plan.setCurrentUnitId(null);
            plan.setUpdateTime(LocalDateTime.now());
            planMapper.updateById(plan);
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_COMPLETED,
                    "词表中的词已经全部安排到场景中");
        }
        int unitNo = nextUnitNo(plan.getId());
        AgentChatResponse aiResponse = generateScene(plan, unitNo, candidates, modelConfigId);
        JsonNode scene = parseScene(aiResponse.getContent());
        List<JsonNode> words = validateSceneWords(scene, candidates);
        LocalDateTime now = LocalDateTime.now();

        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setPlanId(plan.getId());
        unit.setUnitNo(unitNo);
        unit.setTitle(requiredText(scene, "title"));
        unit.setScenarioType(text(scene, "scenario_type", "scenarioType"));
        unit.setSummary(text(scene, "summary", "description"));
        unit.setStatus(LearningConstants.ScenePlan.UNIT_IN_PROGRESS);
        unit.setCoreWordCount(LearningConstants.ZERO);
        unit.setExtendedWordCount(LearningConstants.ZERO);
        unit.setSupplementaryWordCount(LearningConstants.ZERO);
        unit.setCompletedCoreCount(LearningConstants.ZERO);
        unit.setGeneratedTime(now);
        unit.setStartedTime(now);
        unit.setDeleted(false);
        unit.setCreateTime(now);
        unit.setUpdateTime(now);
        unitMapper.insert(unit);

        LearningSceneMaterial material = new LearningSceneMaterial();
        material.setUserId(userId);
        material.setPlanId(plan.getId());
        material.setUnitId(unit.getId());
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

        Map<String, VocabularyCatalogEntry> candidateMap = candidates.stream().collect(Collectors.toMap(
                entry -> normalize(entry.effectiveTerm()), entry -> entry, (left, right) -> left, LinkedHashMap::new));
        int coreCount = LearningConstants.ZERO;
        int extendedCount = LearningConstants.ZERO;
        int supplementaryCount = LearningConstants.ZERO;
        int sortOrder = LearningConstants.FIRST_SEQUENCE;
        for (JsonNode word : words) {
            String term = requiredText(word, "term", "word");
            String normalizedTerm = normalize(term);
            VocabularyCatalogEntry source = candidateMap.get(normalizedTerm);
            String requestedTier = normalizeTier(text(word, "tier"));
            String tier = source == null ? LearningConstants.ScenePlan.TIER_SUPPLEMENTARY : requestedTier;
            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier) && source == null) {
                throw sceneInvalid("核心词必须来自本次候选词表: " + term);
            }
            String requirement = normalizeRequirement(text(word, "mastery_requirement", "masteryRequirement"));
            LearningWordProgress before = progressService.find(userId, normalizedTerm);
            boolean firstLearning = LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                    && (before == null
                    || LearningConstants.ScenePlan.PROGRESS_UNSEEN.equals(before.getLearningState())
                    || LearningConstants.ScenePlan.PROGRESS_EXPOSED.equals(before.getLearningState()));
            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier) && before != null
                    && (LearningConstants.ScenePlan.PROGRESS_REVIEWING.equals(before.getLearningState())
                    || LearningConstants.ScenePlan.PROGRESS_MASTERED.equals(before.getLearningState()))) {
                tier = LearningConstants.ScenePlan.TIER_REVIEW;
                firstLearning = false;
            }
            LearningWordProgress progress = progressService.recordSceneExposure(
                    userId, term, requirement, tier, plan.getId(), unit.getId());
            LearningWordbookEntry wordbookEntry = ensureWordbookEntry(plan, source, progress, term, normalizedTerm, tier, now);

            JsonNode question = node(word, "meaning_question", "meaningQuestion", "assessment");
            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                    || LearningConstants.ScenePlan.TIER_REVIEW.equals(tier)) {
                validateMeaningQuestion(term, question);
            }
            List<String> acceptedSpellings = acceptedSpellings(word, term);
            LearningPlanUnitEntry unitEntry = new LearningPlanUnitEntry();
            unitEntry.setPlanId(plan.getId());
            unitEntry.setUnitId(unit.getId());
            unitEntry.setCatalogEntryId(source == null ? null : source.getId());
            unitEntry.setWordbookEntryId(wordbookEntry == null ? null : wordbookEntry.getId());
            unitEntry.setWordProgressId(progress.getId());
            unitEntry.setSourceOrder(source == null ? null : source.getSourceOrder());
            unitEntry.setTerm(source == null ? term : source.effectiveTerm());
            unitEntry.setNormalizedTerm(source == null ? normalizedTerm : source.getNormalizedTerm());
            unitEntry.setPhonetic(firstText(text(word, "phonetic"), source == null ? null : source.getPhonetic()));
            unitEntry.setMeaningText(firstText(text(word, "meaning", "definition"),
                    source == null ? null : source.getDefinitionText()));
            unitEntry.setContextMeaning(text(word, "context_meaning", "contextMeaning"));
            unitEntry.setTier(tier);
            unitEntry.setMasteryRequirement(requirement);
            unitEntry.setAcceptedSpellingsJson(writeJson(acceptedSpellings));
            unitEntry.setAssessmentJson(question == null || question.isMissingNode() ? null : writeJson(question));
            unitEntry.setFirstLearning(firstLearning);
            unitEntry.setSortOrder(sortOrder++);
            unitEntry.setDeleted(false);
            unitEntry.setCreateTime(now);
            unitEntry.setUpdateTime(now);
            unitEntryMapper.insert(unitEntry);

            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)) {
                coreCount++;
            } else if (LearningConstants.ScenePlan.TIER_SUPPLEMENTARY.equals(tier)) {
                supplementaryCount++;
            } else {
                extendedCount++;
            }
        }

        unit.setCoreWordCount(coreCount);
        unit.setExtendedWordCount(extendedCount);
        unit.setSupplementaryWordCount(supplementaryCount);
        unit.setSceneMaterialId(material.getId());
        unit.setUpdateTime(now);
        unitMapper.updateById(unit);
        plan.setCurrentUnitId(unit.getId());
        plan.setAiSessionId(aiResponse.getSessionId());
        plan.setUpdateTime(now);
        planMapper.updateById(plan);

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "生成场景学习单元",
                plan.getName() + " / " + unit.getTitle() + "，核心词 " + coreCount + " 个");
        log.info("用户「{}」为计划「{}」生成了第 {} 个场景「{}」，核心词 {} 个、扩展词 {} 个、补充词 {} 个",
                userDisplayNameService.userName(userId), plan.getName(), unitNo, unit.getTitle(),
                coreCount, extendedCount, supplementaryCount);
        return toUnitResponse(unit);
    }

    /**
     * 提交四选一、跟敲或按含义拼写结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningAssessmentSubmitResponse submitAssessment(Long userId, Long planId, Long unitId,
                                                             LearningAssessmentSubmitRequest request) {
        LearningPlan plan = requirePlan(userId, planId);
        LearningPlanUnit unit = requireUnit(plan, unitId);
        LearningPlanUnitEntry entry = unitEntryMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                .eq(LearningPlanUnitEntry::getId, request.getUnitEntryId())
                .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                .eq(LearningPlanUnitEntry::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (entry == null || entry.getWordbookEntryId() == null) {
            throw assessmentInvalid("该词当前仅用于场景展示，提升为核心词后才能参加检查");
        }
        String type = normalizeAssessmentType(request.getAssessmentType());
        if (!LearningConstants.ScenePlan.MASTERY_SPELLING.equals(entry.getMasteryRequirement())
                && !LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE.equals(type)) {
            throw assessmentInvalid("该词的掌握要求是认识，不需要拼写检查");
        }

        JsonNode question = readTree(entry.getAssessmentJson());
        String correctAnswer;
        boolean correct;
        double typingAccuracy = 100D;
        if (LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE.equals(type)) {
            correctAnswer = requiredText(question, "correct_answer", "correctAnswer", "answer");
            correct = normalizeAnswer(request.getAnswer()).equals(normalizeAnswer(correctAnswer));
        } else {
            List<String> accepted = readStringList(entry.getAcceptedSpellingsJson());
            if (accepted.isEmpty()) {
                accepted = List.of(entry.getTerm());
            }
            correctAnswer = accepted.get(0);
            String answer = normalizeSpelling(request.getAnswer());
            correct = accepted.stream().map(this::normalizeSpelling).anyMatch(answer::equals);
            typingAccuracy = spellingAccuracy(answer, accepted);
        }

        ReviewSubmitRequest reviewRequest = new ReviewSubmitRequest();
        reviewRequest.setResult(correct
                ? LearningConstants.Review.RESULT_REMEMBERED
                : LearningConstants.Review.RESULT_FORGOTTEN);
        reviewRequest.setScore(correct ? LearningConstants.Review.MAX_MASTERY : LearningConstants.Review.MIN_MASTERY);
        reviewRequest.setDurationSeconds(request.getDurationMillis() == null
                ? null
                : Math.toIntExact(Math.min(Integer.MAX_VALUE, request.getDurationMillis() / 1000L)));
        reviewRequest.setWordProgressId(entry.getWordProgressId());
        reviewRequest.setPlanId(plan.getId());
        reviewRequest.setUnitId(unit.getId());
        reviewRequest.setAssessmentType(type);
        reviewRequest.setQuestionJson(entry.getAssessmentJson());
        reviewRequest.setAnswerText(request.getAnswer());
        reviewRequest.setCorrectAnswer(correctAnswer);
        reviewRequest.setCheckResult(correct
                ? LearningConstants.ScenePlan.CHECK_CORRECT
                : LearningConstants.ScenePlan.CHECK_INCORRECT);
        reviewRequest.setTypingAccuracy(typingAccuracy);
        reviewRequest.setHintLevel(request.getHintLevel());
        reviewRequest.setAttemptCount(request.getAttemptCount());
        reviewRequest.setDurationMillis(request.getDurationMillis());
        ReviewSubmitResponse review = wordbookService.submitReview(userId, entry.getWordbookEntryId(), reviewRequest);
        LearningWordProgress progress = progressService.recordAssessment(
                entry.getWordProgressId(), type, correct, review.getNextReviewTime());
        int completedCoreCount = refreshCompletedCoreCount(unit);

        LearningAssessmentSubmitResponse response = new LearningAssessmentSubmitResponse();
        response.setUnitEntryId(entry.getId());
        response.setAssessmentType(type);
        response.setCorrect(correct);
        response.setCorrectAnswer(correctAnswer);
        response.setTypingAccuracy(typingAccuracy);
        response.setLearningState(progress.getLearningState());
        response.setRecognitionScore(progress.getRecognitionScore());
        response.setSpellingScore(progress.getSpellingScore());
        response.setCompletedCoreCount(completedCoreCount);
        response.setCoreWordCount(unit.getCoreWordCount());
        response.setUnitReadyToComplete(completedCoreCount >= value(unit.getCoreWordCount()));
        response.setNextReviewTime(review.getNextReviewTime());
        return response;
    }

    /**
     * 完成当前场景。下一个场景仍需学习者再次手动触发。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse completeUnit(Long userId, Long planId, Long unitId) {
        LearningPlan plan = requirePlan(userId, planId);
        LearningPlanUnit unit = requireUnit(plan, unitId);
        int completedCore = refreshCompletedCoreCount(unit);
        if (completedCore < value(unit.getCoreWordCount())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_UNIT_INCOMPLETE,
                    "还有 " + (value(unit.getCoreWordCount()) - completedCore) + " 个核心词未通过本场景检查");
        }
        if (!LearningConstants.ScenePlan.UNIT_COMPLETED.equals(unit.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            unit.setStatus(LearningConstants.ScenePlan.UNIT_COMPLETED);
            unit.setCompletedTime(now);
            unit.setUpdateTime(now);
            unitMapper.updateById(unit);
            int newlyLearned = unitEntryMapper.selectCount(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                    .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                    .eq(LearningPlanUnitEntry::getTier, LearningConstants.ScenePlan.TIER_CORE)
                    .eq(LearningPlanUnitEntry::getFirstLearning, true)
                    .eq(LearningPlanUnitEntry::getDeleted, false)).intValue();
            plan.setLearnedCoreWords(value(plan.getLearnedCoreWords()) + newlyLearned);
            plan.setCompletedUnitCount(value(plan.getCompletedUnitCount()) + LearningConstants.SEQUENCE_STEP);
            plan.setCurrentUnitId(null);
            if (nextCandidates(plan).isEmpty()) {
                plan.setStatus(LearningConstants.ScenePlan.STATUS_COMPLETED);
            }
            plan.setUpdateTime(now);
            planMapper.updateById(plan);
            systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "完成场景学习单元",
                    plan.getName() + " / " + unit.getTitle());
            log.info("用户「{}」完成了计划「{}」中的场景「{}」，可继续手动生成下一个场景",
                    userDisplayNameService.userName(userId), plan.getName(), unit.getTitle());
        }
        return detail(userId, planId);
    }

    /**
     * 将扩展或补充词提升为本场景核心词，并加入个人单词本。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanUnitEntryResponse promoteWord(Long userId, Long planId, Long unitId, Long unitEntryId) {
        LearningPlan plan = requirePlan(userId, planId);
        LearningPlanUnit unit = requireUnit(plan, unitId);
        LearningPlanUnitEntry entry = unitEntryMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                .eq(LearningPlanUnitEntry::getId, unitEntryId)
                .eq(LearningPlanUnitEntry::getUnitId, unitId)
                .eq(LearningPlanUnitEntry::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (entry == null) {
            throw assessmentInvalid("场景词汇不存在");
        }
        if (!LearningConstants.ScenePlan.TIER_CORE.equals(entry.getTier())) {
            String previousTier = entry.getTier();
            LearningWordProgress progress = progressService.recordSceneExposure(
                    userId, entry.getTerm(), LearningConstants.ScenePlan.MASTERY_RECOGNITION,
                    LearningConstants.ScenePlan.TIER_CORE, planId, unitId);
            VocabularyCatalogEntry source = entry.getCatalogEntryId() == null
                    ? null
                    : catalogEntryMapper.selectById(entry.getCatalogEntryId());
            LearningWordbookEntry wordbookEntry = ensureWordbookEntry(
                    plan, source, progress, entry.getTerm(), entry.getNormalizedTerm(),
                    LearningConstants.ScenePlan.TIER_CORE, LocalDateTime.now());
            entry.setTier(LearningConstants.ScenePlan.TIER_CORE);
            entry.setMasteryRequirement(LearningConstants.ScenePlan.MASTERY_RECOGNITION);
            entry.setWordProgressId(progress.getId());
            entry.setWordbookEntryId(wordbookEntry == null ? null : wordbookEntry.getId());
            entry.setFirstLearning(true);
            ensurePromotionAssessment(entry, unit);
            entry.setUpdateTime(LocalDateTime.now());
            unitEntryMapper.updateById(entry);
            unit.setCoreWordCount(value(unit.getCoreWordCount()) + LearningConstants.SEQUENCE_STEP);
            if (LearningConstants.ScenePlan.TIER_SUPPLEMENTARY.equals(previousTier)) {
                unit.setSupplementaryWordCount(Math.max(0, value(unit.getSupplementaryWordCount()) - 1));
            } else {
                unit.setExtendedWordCount(Math.max(0, value(unit.getExtendedWordCount()) - 1));
            }
            unit.setUpdateTime(LocalDateTime.now());
            unitMapper.updateById(unit);
        }
        return toUnitEntryResponse(entry);
    }

    private void ensurePromotionAssessment(LearningPlanUnitEntry entry, LearningPlanUnit unit) {
        if (StringUtils.hasText(entry.getAssessmentJson())) {
            return;
        }
        LinkedHashSet<String> options = unitEntryMapper.selectList(
                        new LambdaQueryWrapper<LearningPlanUnitEntry>()
                                .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                                .eq(LearningPlanUnitEntry::getDeleted, false)
                                .orderByAsc(LearningPlanUnitEntry::getSortOrder))
                .stream()
                .map(LearningPlanUnitEntry::getMeaningText)
                .filter(StringUtils::hasText)
                .filter(meaning -> !normalizeAnswer(meaning).equals(normalizeAnswer(entry.getMeaningText())))
                .limit(3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!StringUtils.hasText(entry.getMeaningText()) || options.size() < 3) {
            throw assessmentInvalid("当前场景缺少足够的含义选项，暂时无法把该词提升为核心词");
        }
        options.add(entry.getMeaningText());
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("prompt", "请选择“" + entry.getTerm() + "”在当前场景中的正确含义");
        question.put("options", List.copyOf(options));
        question.put("correct_answer", entry.getMeaningText());
        entry.setAssessmentJson(writeJson(question));
    }

    private AgentChatResponse generateScene(LearningPlan plan, int unitNo,
                                            List<VocabularyCatalogEntry> candidates, Long modelConfigId) {
        List<CandidateWord> words = candidates.stream()
                .map(entry -> new CandidateWord(entry.getSourceOrder(), entry.effectiveTerm(),
                        entry.getPhonetic(), entry.getDefinitionText()))
                .toList();
        List<String> completedScenes = unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                        .eq(LearningPlanUnit::getPlanId, plan.getId())
                        .eq(LearningPlanUnit::getDeleted, false)
                        .orderByAsc(LearningPlanUnit::getUnitNo))
                .stream()
                .map(LearningPlanUnit::getTitle)
                .toList();
        Map<String, Object> variables = new HashMap<>();
        variables.put("learning_purpose", StrUtil.blankToDefault(plan.getLearningPurpose(), "综合英语词汇学习"));
        variables.put("unit_no", unitNo);
        variables.put("candidate_words", words);
        variables.put("completed_scenes", completedScenes);

        AgentChatRequest request = new AgentChatRequest();
        request.setAgentCode(LearningConstants.VOCABULARY_PLAN_AGENT_CODE);
        request.setTemplateCode(LearningConstants.VOCABULARY_PLAN_TEMPLATE_CODE);
        request.setSessionId(plan.getAiSessionId());
        request.setTitle(LearningScene.ENGLISH_VOCABULARY_PLAN.getTitle());
        request.setBusinessType(LearningConstants.ChatSession.BUSINESS_TYPE_LEARNING);
        request.setBusinessId(LearningScene.ENGLISH_VOCABULARY_PLAN.getCode());
        request.setSceneCode(LearningScene.ENGLISH_VOCABULARY_PLAN.getCode());
        request.setModelConfigId(modelConfigId);
        request.setMessage("请为学习计划“" + plan.getName() + "”生成第 " + unitNo + " 个场景单元。");
        request.setVariables(variables);
        return aiChatService.chat(request);
    }

    private List<VocabularyCatalogEntry> nextCandidates(LearningPlan plan) {
        Set<Long> arranged = unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getPlanId, plan.getId())
                        .isNotNull(LearningPlanUnitEntry::getCatalogEntryId)
                        .in(LearningPlanUnitEntry::getTier,
                                List.of(LearningConstants.ScenePlan.TIER_CORE, LearningConstants.ScenePlan.TIER_REVIEW))
                        .eq(LearningPlanUnitEntry::getDeleted, false))
                .stream()
                .map(LearningPlanUnitEntry::getCatalogEntryId)
                .collect(Collectors.toSet());
        List<VocabularyCatalogEntry> all = catalogEntryMapper.selectList(
                new LambdaQueryWrapper<VocabularyCatalogEntry>()
                        .eq(VocabularyCatalogEntry::getCatalogVersionId, plan.getCatalogVersionId())
                        .eq(VocabularyCatalogEntry::getPublished, true)
                        .eq(VocabularyCatalogEntry::getDeleted, false)
                        .orderByAsc(VocabularyCatalogEntry::getSourceOrder));
        List<VocabularyCatalogEntry> result = new ArrayList<>();
        for (VocabularyCatalogEntry entry : all) {
            if (arranged.contains(entry.getId())) {
                continue;
            }
            LearningWordProgress progress = progressService.find(plan.getUserId(), entry.getNormalizedTerm());
            if (progress != null && LearningConstants.ScenePlan.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                continue;
            }
            result.add(entry);
            if (result.size() >= LearningConstants.ScenePlan.CANDIDATE_WORD_LIMIT) {
                break;
            }
        }
        return result;
    }

    private List<JsonNode> validateSceneWords(JsonNode scene, List<VocabularyCatalogEntry> candidates) {
        JsonNode vocabulary = node(scene, "vocabulary", "words");
        if (vocabulary == null || !vocabulary.isArray() || vocabulary.isEmpty()) {
            throw sceneInvalid("AI 场景结果缺少 vocabulary 数组");
        }
        Set<String> candidateTerms = candidates.stream()
                .map(entry -> normalize(entry.effectiveTerm()))
                .collect(Collectors.toSet());
        int coreCount = LearningConstants.ZERO;
        List<JsonNode> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode word : vocabulary) {
            String term = requiredText(word, "term", "word");
            String normalized = normalize(term);
            if (!seen.add(normalized)) {
                continue;
            }
            String tier = normalizeTier(text(word, "tier"));
            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)) {
                if (!candidateTerms.contains(normalized)) {
                    throw sceneInvalid("AI 返回了不在候选词表中的核心词: " + term);
                }
                coreCount++;
            }
            result.add(word);
        }
        int requiredMinimum = Math.min(LearningConstants.ScenePlan.MIN_CORE_WORDS, candidates.size());
        if (coreCount < requiredMinimum || coreCount > LearningConstants.ScenePlan.MAX_CORE_WORDS) {
            throw sceneInvalid("核心词数量应为 " + requiredMinimum + "-"
                    + LearningConstants.ScenePlan.MAX_CORE_WORDS + " 个，实际为 " + coreCount + " 个");
        }
        return result;
    }

    private void validateMeaningQuestion(String term, JsonNode question) {
        if (question == null || question.isMissingNode() || question.isNull()) {
            throw sceneInvalid("核心词缺少含义四选一题: " + term);
        }
        JsonNode options = node(question, "options");
        if (options == null || !options.isArray() || options.size() != 4) {
            throw sceneInvalid("核心词的含义题必须包含 4 个选项: " + term);
        }
        String correct = requiredText(question, "correct_answer", "correctAnswer", "answer");
        boolean contained = false;
        for (JsonNode option : options) {
            if (normalizeAnswer(option.asText()).equals(normalizeAnswer(correct))) {
                contained = true;
                break;
            }
        }
        if (!contained) {
            throw sceneInvalid("核心词含义题的正确答案不在选项中: " + term);
        }
    }

    private LearningWordbookEntry ensureWordbookEntry(LearningPlan plan, VocabularyCatalogEntry source,
                                                       LearningWordProgress progress, String term,
                                                       String normalizedTerm, String tier, LocalDateTime now) {
        if (LearningConstants.ScenePlan.TIER_SUPPLEMENTARY.equals(tier)) {
            return null;
        }
        LearningWordbookEntry existing = wordbookEntryMapper.selectIncludingDeleted(
                plan.getWordbookId(), normalizedTerm);
        if (existing == null) {
            String snapshot = basicSnapshot(source, term);
            existing = LearningWordbookEntry.createImported(
                    plan.getUserId(), plan.getWordbookId(), progress.getId(), source == null ? null : source.getId(),
                    source == null ? term : source.effectiveTerm(), normalizedTerm, snapshot, now);
            wordbookEntryMapper.insert(existing);
        } else {
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                existing.restore(existing.getNote(), now);
                wordbookEntryMapper.restoreDeletedById(existing.getId());
            }
            existing.setProgressId(progress.getId());
            if (source != null) {
                existing.setCatalogEntryId(source.getId());
            }
            if ((LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                    || LearningConstants.ScenePlan.TIER_REVIEW.equals(tier))
                    && !LearningConstants.VocabularyCard.STATUS_READY.equals(existing.getCardStatus())) {
                existing.setCardStatus(LearningConstants.VocabularyCard.STATUS_MISSING);
            }
            existing.setUpdateTime(now);
            wordbookEntryMapper.updateById(existing);
        }
        return existing;
    }

    private int refreshCompletedCoreCount(LearningPlanUnit unit) {
        List<LearningPlanUnitEntry> coreEntries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                        .eq(LearningPlanUnitEntry::getTier, LearningConstants.ScenePlan.TIER_CORE)
                        .eq(LearningPlanUnitEntry::getDeleted, false));
        List<LearningReviewRecord> records = reviewRecordMapper.selectList(new LambdaQueryWrapper<LearningReviewRecord>()
                .eq(LearningReviewRecord::getUnitId, unit.getId())
                .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                .eq(LearningReviewRecord::getDeleted, false));
        Map<Long, Set<String>> passedTypes = records.stream()
                .collect(Collectors.groupingBy(LearningReviewRecord::getEntryId,
                        Collectors.mapping(LearningReviewRecord::getAssessmentType, Collectors.toSet())));
        int completed = LearningConstants.ZERO;
        for (LearningPlanUnitEntry entry : coreEntries) {
            Set<String> passed = passedTypes.getOrDefault(entry.getWordbookEntryId(), Set.of());
            boolean meaningPassed = passed.contains(LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE);
            boolean spellingPassed = !LearningConstants.ScenePlan.MASTERY_SPELLING.equals(entry.getMasteryRequirement())
                    || (passed.contains(LearningConstants.ScenePlan.ASSESSMENT_COPY_TYPING)
                    && passed.contains(LearningConstants.ScenePlan.ASSESSMENT_MEANING_SPELLING));
            if (meaningPassed && spellingPassed) {
                completed++;
            }
        }
        unit.setCompletedCoreCount(completed);
        unit.setUpdateTime(LocalDateTime.now());
        unitMapper.updateById(unit);
        return completed;
    }

    private LearningPlanResponse toPlanResponse(LearningPlan plan, boolean includeUnits) {
        LearningPlanResponse response = new LearningPlanResponse();
        response.setId(plan.getId());
        response.setCatalogId(plan.getCatalogId());
        response.setCatalogVersionId(plan.getCatalogVersionId());
        response.setWordbookId(plan.getWordbookId());
        response.setName(plan.getName());
        response.setLearningPurpose(plan.getLearningPurpose());
        response.setStatus(plan.getStatus());
        response.setTotalCatalogWords(plan.getTotalCatalogWords());
        response.setLearnedCoreWords(plan.getLearnedCoreWords());
        response.setCompletedUnitCount(plan.getCompletedUnitCount());
        response.setCurrentUnitId(plan.getCurrentUnitId());
        response.setAiSessionId(plan.getAiSessionId());
        response.setCanGenerateNext(LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus())
                && plan.getCurrentUnitId() == null);
        response.setUnits(includeUnits
                ? unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                                .eq(LearningPlanUnit::getPlanId, plan.getId())
                                .eq(LearningPlanUnit::getDeleted, false)
                                .orderByAsc(LearningPlanUnit::getUnitNo))
                        .stream().map(this::toUnitResponse).toList()
                : List.of());
        response.setCreateTime(plan.getCreateTime());
        response.setUpdateTime(plan.getUpdateTime());
        return response;
    }

    private LearningPlanUnitResponse toUnitResponse(LearningPlanUnit unit) {
        LearningSceneMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<LearningSceneMaterial>()
                .eq(LearningSceneMaterial::getUnitId, unit.getId())
                .eq(LearningSceneMaterial::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        LearningPlanUnitResponse response = new LearningPlanUnitResponse();
        response.setId(unit.getId());
        response.setPlanId(unit.getPlanId());
        response.setUnitNo(unit.getUnitNo());
        response.setTitle(unit.getTitle());
        response.setScenarioType(unit.getScenarioType());
        response.setSummary(unit.getSummary());
        response.setStatus(unit.getStatus());
        response.setCoreWordCount(unit.getCoreWordCount());
        response.setExtendedWordCount(unit.getExtendedWordCount());
        response.setSupplementaryWordCount(unit.getSupplementaryWordCount());
        response.setCompletedCoreCount(unit.getCompletedCoreCount());
        response.setRecommendedDate(unit.getRecommendedDate());
        response.setLearningText(material == null ? null : material.getLearningText());
        response.setTranslation(material == null ? null : material.getTranslation());
        response.setMaterial(material == null ? null : readJson(material.getParsedJson()));
        List<LearningPlanUnitEntry> unitEntries = unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                .eq(LearningPlanUnitEntry::getDeleted, false)
                .orderByAsc(LearningPlanUnitEntry::getSortOrder));
        Set<Long> progressIds = unitEntries.stream()
                .map(LearningPlanUnitEntry::getWordProgressId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LearningWordProgress> progressMap = progressIds.isEmpty()
                ? Map.of()
                : progressMapper.selectBatchIds(progressIds).stream()
                        .collect(Collectors.toMap(LearningWordProgress::getId, progress -> progress));
        Map<Long, List<String>> passedMap = reviewRecordMapper.selectList(new LambdaQueryWrapper<LearningReviewRecord>()
                        .eq(LearningReviewRecord::getUnitId, unit.getId())
                        .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                        .eq(LearningReviewRecord::getDeleted, false))
                .stream()
                .filter(record -> record.getEntryId() != null && record.getAssessmentType() != null)
                .collect(Collectors.groupingBy(LearningReviewRecord::getEntryId,
                        Collectors.mapping(LearningReviewRecord::getAssessmentType,
                                Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), List::copyOf))));
        response.setWords(unitEntries.stream()
                .map(entry -> toUnitEntryResponse(entry, progressMap.get(entry.getWordProgressId()), passedMap))
                .toList());
        response.setGeneratedTime(unit.getGeneratedTime());
        response.setCompletedTime(unit.getCompletedTime());
        return response;
    }

    private LearningPlanUnitEntryResponse toUnitEntryResponse(LearningPlanUnitEntry entry) {
        LearningWordProgress progress = progressMapper.selectById(entry.getWordProgressId());
        Map<Long, List<String>> passedMap = entry.getWordbookEntryId() == null
                ? Map.of()
                : Map.of(entry.getWordbookEntryId(), reviewRecordMapper.selectList(
                                new LambdaQueryWrapper<LearningReviewRecord>()
                                        .eq(LearningReviewRecord::getUnitId, entry.getUnitId())
                                        .eq(LearningReviewRecord::getEntryId, entry.getWordbookEntryId())
                                        .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                                        .eq(LearningReviewRecord::getDeleted, false))
                        .stream().map(LearningReviewRecord::getAssessmentType)
                        .filter(Objects::nonNull).distinct().toList());
        return toUnitEntryResponse(entry, progress, passedMap);
    }

    private LearningPlanUnitEntryResponse toUnitEntryResponse(LearningPlanUnitEntry entry,
                                                              LearningWordProgress progress,
                                                              Map<Long, List<String>> passedMap) {
        LearningPlanUnitEntryResponse response = new LearningPlanUnitEntryResponse();
        response.setId(entry.getId());
        response.setCatalogEntryId(entry.getCatalogEntryId());
        response.setWordbookEntryId(entry.getWordbookEntryId());
        response.setWordProgressId(entry.getWordProgressId());
        response.setSourceOrder(entry.getSourceOrder());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setPhonetic(entry.getPhonetic());
        response.setMeaning(entry.getMeaningText());
        response.setContextMeaning(entry.getContextMeaning());
        response.setTier(entry.getTier());
        response.setMasteryRequirement(entry.getMasteryRequirement());
        response.setAcceptedSpellings(readStringList(entry.getAcceptedSpellingsJson()));
        response.setAssessment(readJson(entry.getAssessmentJson()));
        response.setPassedAssessments(entry.getWordbookEntryId() == null
                ? List.of()
                : passedMap.getOrDefault(entry.getWordbookEntryId(), List.of()));
        response.setFirstLearning(entry.getFirstLearning());
        if (progress != null) {
            response.setLearningState(progress.getLearningState());
            response.setRecognitionScore(progress.getRecognitionScore());
            response.setSpellingScore(progress.getSpellingScore());
            response.setCardStatus(progress.getCardStatus());
        }
        return response;
    }

    private LearningPlan requirePlan(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.LEARNING_PLAN_NOT_FOUND,
                    "学习计划不存在: " + planId);
        }
        return plan;
    }

    private LearningPlanUnit requireUnit(LearningPlan plan, Long unitId) {
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .eq(LearningPlanUnit::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (unit == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND,
                    "场景学习单元不存在: " + unitId);
        }
        return unit;
    }

    private LearningPlanUnit findActiveUnit(Long planId) {
        return unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .in(LearningPlanUnit::getStatus,
                        List.of(LearningConstants.ScenePlan.UNIT_READY, LearningConstants.ScenePlan.UNIT_IN_PROGRESS))
                .eq(LearningPlanUnit::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private int nextUnitNo(Long planId) {
        LearningPlanUnit last = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByDesc(LearningPlanUnit::getUnitNo)
                .last(LearningConstants.SQL_LIMIT_ONE));
        return last == null ? LearningConstants.FIRST_SEQUENCE : value(last.getUnitNo()) + 1;
    }

    private VocabularyCatalogVersion requirePublishedVersion(Long userId, Long versionId) {
        VocabularyCatalogVersion version = catalogVersionMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalogVersion>()
                .eq(VocabularyCatalogVersion::getId, versionId)
                .eq(VocabularyCatalogVersion::getStatus, LearningConstants.VocabularyImport.VERSION_STATUS_PUBLISHED)
                .eq(VocabularyCatalogVersion::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (version == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_CATALOG_NOT_FOUND,
                    "已发布词表版本不存在: " + versionId);
        }
        requireCatalog(userId, version.getCatalogId());
        return version;
    }

    private VocabularyCatalog requireCatalog(Long userId, Long catalogId) {
        VocabularyCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<VocabularyCatalog>()
                .eq(VocabularyCatalog::getId, catalogId)
                .and(wrapper -> wrapper.eq(VocabularyCatalog::getOwnerUserId, userId)
                        .or().eq(VocabularyCatalog::getVisibility, LearningConstants.VocabularyImport.VISIBILITY_PUBLIC))
                .eq(VocabularyCatalog::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (catalog == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.VOCABULARY_CATALOG_NOT_FOUND,
                    "词表不存在: " + catalogId);
        }
        return catalog;
    }

    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectOne(new LambdaQueryWrapper<LearningWordbook>()
                .eq(LearningWordbook::getId, wordbookId)
                .eq(LearningWordbook::getUserId, userId)
                .eq(LearningWordbook::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (wordbook == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.WORDBOOK_NOT_FOUND,
                    "单词本不存在: " + wordbookId);
        }
        return wordbook;
    }

    private JsonNode parseScene(String content) {
        if (!StringUtils.hasText(content)) {
            throw sceneInvalid("AI 未返回场景内容");
        }
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleaned);
            if (matcher.find()) {
                try {
                    return objectMapper.readTree(matcher.group());
                } catch (Exception ex) {
                    log.debug("场景 JSON 二次提取失败 error={}", ex.getMessage());
                }
            }
            throw sceneInvalid("AI 返回的场景不是有效 JSON，请重试生成");
        }
    }

    private String basicSnapshot(VocabularyCatalogEntry source, String term) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("meaning", source == null ? null : source.getDefinitionText());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("term", term);
        snapshot.put("phonetic", source == null ? null : source.getPhonetic());
        snapshot.put("definitions", List.of(definition));
        snapshot.put("importedBasicCard", true);
        return writeJson(snapshot);
    }

    private List<String> acceptedSpellings(JsonNode word, String term) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(term);
        JsonNode accepted = node(word, "accepted_spellings", "acceptedSpellings");
        if (accepted != null && accepted.isArray()) {
            accepted.forEach(item -> {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    result.add(item.asText().trim());
                }
            });
        }
        if (term.contains("-")) {
            result.add(term.replace('-', ' '));
        }
        return List.copyOf(result);
    }

    private String normalizeTier(String tier) {
        if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                || LearningConstants.ScenePlan.TIER_EXTENDED.equals(tier)
                || LearningConstants.ScenePlan.TIER_SUPPLEMENTARY.equals(tier)) {
            return tier;
        }
        return LearningConstants.ScenePlan.TIER_EXTENDED;
    }

    private String normalizeRequirement(String requirement) {
        return LearningConstants.ScenePlan.MASTERY_SPELLING.equals(requirement)
                ? LearningConstants.ScenePlan.MASTERY_SPELLING
                : LearningConstants.ScenePlan.MASTERY_RECOGNITION;
    }

    private String normalizeAssessmentType(String type) {
        String normalized = normalize(type);
        if (LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE.equals(normalized)
                || LearningConstants.ScenePlan.ASSESSMENT_COPY_TYPING.equals(normalized)
                || LearningConstants.ScenePlan.ASSESSMENT_MEANING_SPELLING.equals(normalized)) {
            return normalized;
        }
        throw assessmentInvalid("不支持的检查类型: " + type);
    }

    private double spellingAccuracy(String answer, List<String> accepted) {
        int bestDistance = accepted.stream()
                .map(this::normalizeSpelling)
                .mapToInt(candidate -> levenshtein(answer, candidate))
                .min()
                .orElse(answer.length());
        int maxLength = Math.max(1, Math.max(answer.length(), accepted.stream()
                .map(this::normalizeSpelling).mapToInt(String::length).max().orElse(1)));
        return Math.max(0D, Math.round((1D - (double) bestDistance / maxLength) * 10_000D) / 100D);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
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

    private Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            log.debug("场景 JSON 读取失败 error={}", ex.getMessage());
            return null;
        }
    }

    private JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw assessmentInvalid("检查题数据已损坏，请重新生成当前场景");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.debug("可接受拼写 JSON 读取失败 error={}", ex.getMessage());
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    "场景学习数据序列化失败",
                    ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeAnswer(String value) {
        return normalize(value).replaceAll("[，。；;,.!?！？]$", "");
    }

    private String normalizeSpelling(String value) {
        return normalize(value).replace('’', '\'');
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private int value(Integer value) {
        return value == null ? LearningConstants.ZERO : value;
    }

    private LearningAssistantException sceneInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.LEARNING_SCENE_PARSE_FAILED,
                message);
    }

    private LearningAssistantException assessmentInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.LEARNING_ASSESSMENT_INVALID,
                message);
    }

    private record CandidateWord(Integer sourceOrder, String term, String phonetic, String meaning) {
    }
}
