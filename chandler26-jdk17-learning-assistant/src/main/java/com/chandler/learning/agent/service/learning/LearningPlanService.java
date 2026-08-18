package com.chandler.learning.agent.service.learning;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanCreateRequest;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanCalendarDayResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanResponse;
import com.chandler.learning.agent.domain.dto.learning.LearningPlanUpdateRequest;
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
import com.chandler.learning.agent.domain.enums.AiInvocationScene;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建自助学习计划；默认立即生成第一个场景单元。
     */
    public LearningPlanResponse create(Long userId, LearningPlanCreateRequest request) {
        LearningPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> createPlan(userId, request)));
        if (LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus())
                && !Boolean.FALSE.equals(request.getGenerateFirstUnit())) {
            generateNextUnit(userId, plan.getId(), request.getModelConfigId(), null);
        }
        return detail(userId, plan.getId());
    }

    /** 在短事务中创建计划主体，AI 场景生成必须在该事务提交后执行。 */
    private LearningPlan createPlan(Long userId, LearningPlanCreateRequest request) {
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
        plan.setStartTime(request.getStartTime());
        plan.setEndTime(request.getEndTime());
        if (request.getStartTime() != null && request.getStartTime().isAfter(now)) {
            plan.setStatus(LearningConstants.ScenePlan.STATUS_NOT_STARTED);
        } else {
            plan.setStatus(LearningConstants.ScenePlan.STATUS_ACTIVE);
        }
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
        return plan;
    }

    public LearningPlanResponse update(Long userId, Long planId, LearningPlanUpdateRequest request) {
        PlanUpdateResult result = Objects.requireNonNull(transactionTemplate.execute(status ->
                updatePlanState(userId, planId, request)));
        if (result.generateFirstUnit()) {
            try {
                generateNextUnit(userId, planId, request.getModelConfigId(), null);
            } catch (RuntimeException ex) {
                log.warn("计划状态已更新，但自动生成首个场景失败 planId={} error={}", planId, ex.getMessage());
            }
        }
        return detail(userId, planId);
    }

    /** 在短事务中更新计划状态，AI 生成在提交后由调用方触发。 */
    private PlanUpdateResult updatePlanState(Long userId, Long planId, LearningPlanUpdateRequest request) {
        LearningPlan plan = requirePlan(userId, planId);
        LocalDateTime now = LocalDateTime.now();
        boolean generateFirstUnit = false;

        plan.setName(request.getName().trim());
        plan.setLearningPurpose(StringUtils.hasText(request.getLearningPurpose()) ? request.getLearningPurpose().trim() : null);
        plan.setStartTime(request.getStartTime());
        plan.setEndTime(request.getEndTime());
        if (request.getWordbookId() != null && !request.getWordbookId().equals(plan.getWordbookId())) {
            plan.setWordbookId(requireWordbook(userId, request.getWordbookId()).getId());
        }

        if (request.getStatus() != null) {
            String newStatus = request.getStatus().trim();
            if (!newStatus.equals(plan.getStatus())) {
                if (LearningConstants.ScenePlan.STATUS_ACTIVE.equals(newStatus)) {
                    if (!LearningConstants.ScenePlan.STATUS_PAUSED.equals(plan.getStatus())
                            && !LearningConstants.ScenePlan.STATUS_NOT_STARTED.equals(plan.getStatus())) {
                        throw LearningAssistantException.badRequest(
                                LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                                "只有暂停或未开始的计划才可以恢复/启动");
                    }
                    plan.setStatus(LearningConstants.ScenePlan.STATUS_ACTIVE);
                    if (plan.getCurrentUnitId() == null) {
                        generateFirstUnit = true;
                    }
                } else if (LearningConstants.ScenePlan.STATUS_PAUSED.equals(newStatus)) {
                    if (!LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus())) {
                        throw LearningAssistantException.badRequest(
                                LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                                "只有进行中的计划才可以暂停");
                    }
                    plan.setStatus(LearningConstants.ScenePlan.STATUS_PAUSED);
                } else if (LearningConstants.ScenePlan.STATUS_CANCELLED.equals(newStatus)) {
                    if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(plan.getStatus())
                            || LearningConstants.ScenePlan.STATUS_CANCELLED.equals(plan.getStatus())) {
                        throw LearningAssistantException.badRequest(
                                LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                                "已完成或已取消的计划无法取消");
                    }
                    plan.setStatus(LearningConstants.ScenePlan.STATUS_CANCELLED);
                } else if (LearningConstants.ScenePlan.STATUS_NOT_STARTED.equals(newStatus)) {
                    if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(plan.getStatus())
                            || LearningConstants.ScenePlan.STATUS_CANCELLED.equals(plan.getStatus())) {
                        throw LearningAssistantException.badRequest(
                                LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                                "已完成或已取消的计划无法设为未开始");
                    }
                    plan.setStatus(LearningConstants.ScenePlan.STATUS_NOT_STARTED);
                } else if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(newStatus)) {
                    plan.setStatus(LearningConstants.ScenePlan.STATUS_COMPLETED);
                } else {
                    throw LearningAssistantException.badRequest(
                            LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                            "无效的学习计划状态: " + newStatus);
                }
            }
        }

        plan.setUpdateTime(now);
        planMapper.updateById(plan);

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "修改场景学习计划",
                plan.getName() + "，状态: " + plan.getStatus());
        log.info("用户「{}」修改了场景学习计划「{}」，状态 = {}",
                userDisplayNameService.userName(userId), plan.getName(), plan.getStatus());
        return new PlanUpdateResult(plan, generateFirstUnit);
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
     * 查询计划在指定日期范围内的日历汇总，过去日期也会返回，便于查看遗漏任务。
     */
    public List<LearningPlanCalendarDayResponse> calendar(Long userId, Long planId,
                                                          LocalDate from, LocalDate to) {
        LearningPlan plan = requirePlan(userId, planId);
        LocalDate today = LocalDate.now();
        LocalDate resolvedFrom = from == null ? today.with(java.time.DayOfWeek.MONDAY) : from;
        LocalDate resolvedTo = to == null ? resolvedFrom.plusDays(6) : to;
        if (resolvedTo.isBefore(resolvedFrom)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "日历结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > 62) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "单次日历查询不能超过 63 天");
        }
        List<LearningPlanUnit> units = unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .ge(LearningPlanUnit::getRecommendedDate, resolvedFrom)
                .le(LearningPlanUnit::getRecommendedDate, resolvedTo)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getRecommendedDate)
                .orderByAsc(LearningPlanUnit::getUnitNo));
        Map<LocalDate, List<LearningPlanUnitResponse>> unitsByDate = units.stream()
                .map(this::toUnitResponse)
                .collect(Collectors.groupingBy(unit -> unit.getRecommendedDate(), LinkedHashMap::new,
                        Collectors.toList()));
        List<LearningPlanCalendarDayResponse> result = new ArrayList<>();
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            List<LearningPlanUnitResponse> dateUnits = unitsByDate.getOrDefault(date, List.of());
            int planned = dateUnits.stream().mapToInt(unit -> value(unit.getCoreWordCount())).sum();
            int pending = dateUnits.stream().mapToInt(unit -> pendingCoreCount(unit)).sum();
            int completed = (int) dateUnits.stream()
                    .filter(unit -> LearningConstants.ScenePlan.UNIT_COMPLETED.equals(unit.getStatus()))
                    .count();
            LearningPlanCalendarDayResponse day = new LearningPlanCalendarDayResponse();
            day.setDate(date);
            day.setPlannedWordCount(planned);
            day.setPendingChallengeCount(pending);
            day.setGeneratedUnitCount(dateUnits.size());
            day.setCompletedUnitCount(completed);
            day.setOverdueCount(date.isBefore(today) ? pending : LearningConstants.ZERO);
            day.setUnits(dateUnits);
            result.add(day);
        }
        return result;
    }

    /**
     * 按学习计划生成指定日期的场景材料。每日目标超过 50 词时均分为多篇材料。
     */
    public List<LearningPlanUnitResponse> generateNextUnit(Long userId, Long planId, Long modelConfigId,
                                                          LocalDate recommendedDate) {
        LearningPlan plan = requirePlan(userId, planId);
        if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_COMPLETED,
                    "学习计划已经完成");
        }

        LocalDate today = LocalDate.now();
        if (plan.getStartTime() != null && today.isBefore(plan.getStartTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "学习计划尚未开始，暂不可生成场景");
        }
        if (plan.getEndTime() != null && today.isAfter(plan.getEndTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "学习计划已超出结束日期，不可继续生成场景");
        }

        LocalDate resolvedRecommendedDate = resolveRecommendedDate(plan, recommendedDate, today);
        int dailyTarget = targetWordCount(plan);
        List<VocabularyCatalogEntry> candidates = nextCandidates(plan, dailyTarget);
        if (candidates.isEmpty()) {
            if (hasIncompleteUnit(plan.getId())) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                        "词表中的词已经全部安排到场景中，请完成已生成的待学习场景");
            }
            markPlanCompleted(plan);
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_COMPLETED,
                    "词表中的词已经全部安排到场景中");
        }
        int totalToGenerate = Math.min(dailyTarget, candidates.size());
        List<LearningPlanUnitResponse> generatedUnits = new ArrayList<>();
        int candidateOffset = 0;
        for (Integer batchSize : splitMaterialWordCounts(totalToGenerate)) {
            List<VocabularyCatalogEntry> batch = new ArrayList<>(
                    candidates.subList(candidateOffset, candidateOffset + batchSize));
            generatedUnits.add(generateSingleUnit(userId, plan, modelConfigId, resolvedRecommendedDate,
                    today, batch, batchSize));
            candidateOffset += batchSize;
        }
        return List.copyOf(generatedUnits);
    }

    static List<Integer> splitMaterialWordCounts(int totalWordCount) {
        if (totalWordCount <= LearningConstants.ZERO) {
            return List.of();
        }
        int materialCount = (totalWordCount + LearningConstants.ScenePlan.MAX_CORE_WORDS_PER_UNIT - 1)
                / LearningConstants.ScenePlan.MAX_CORE_WORDS_PER_UNIT;
        int baseSize = totalWordCount / materialCount;
        int remainder = totalWordCount % materialCount;
        List<Integer> result = new ArrayList<>(materialCount);
        for (int index = LearningConstants.ZERO; index < materialCount; index++) {
            result.add(baseSize + (index < remainder ? LearningConstants.SEQUENCE_STEP : LearningConstants.ZERO));
        }
        return List.copyOf(result);
    }

    private LearningPlanUnitResponse generateSingleUnit(Long userId, LearningPlan plan, Long modelConfigId,
                                                        LocalDate resolvedRecommendedDate, LocalDate today,
                                                        List<VocabularyCatalogEntry> candidates, int targetWordCount) {
        int unitNo = nextUnitNo(plan.getId());
        AgentChatResponse aiResponse = generateScene(plan, unitNo, candidates, targetWordCount, modelConfigId);
        JsonNode scene = parseScene(aiResponse.getContent());
        List<JsonNode> words = validateSceneWords(scene, candidates, targetWordCount);
        return Objects.requireNonNull(transactionTemplate.execute(status -> persistGeneratedUnit(
                userId, plan, resolvedRecommendedDate, today, candidates, unitNo, aiResponse, scene, words)));
    }

    /** 在短事务中持久化已经完成校验的场景材料。 */
    private LearningPlanUnitResponse persistGeneratedUnit(Long userId, LearningPlan plan,
                                                          LocalDate resolvedRecommendedDate, LocalDate today,
                                                          List<VocabularyCatalogEntry> candidates, int unitNo,
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
        unit.setStatus(startImmediately
                ? LearningConstants.ScenePlan.UNIT_IN_PROGRESS
                : LearningConstants.ScenePlan.UNIT_READY);
        unit.setCoreWordCount(LearningConstants.ZERO);
        unit.setExtendedWordCount(LearningConstants.ZERO);
        unit.setSupplementaryWordCount(LearningConstants.ZERO);
        unit.setCompletedCoreCount(LearningConstants.ZERO);
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

        Set<String> missingTerms = new LinkedHashSet<>();
        for (JsonNode word : words) {
            String nTerm = normalize(requiredText(word, "term", "word"));
            if (!candidateMap.containsKey(nTerm)) {
                missingTerms.add(nTerm);
            }
        }
        if (!missingTerms.isEmpty()) {
            List<VocabularyCatalogEntry> extraCatalogEntries = catalogEntryMapper.selectList(
                    new LambdaQueryWrapper<VocabularyCatalogEntry>()
                            .eq(VocabularyCatalogEntry::getCatalogVersionId, plan.getCatalogVersionId())
                            .in(VocabularyCatalogEntry::getNormalizedTerm, missingTerms)
                            .eq(VocabularyCatalogEntry::getDeleted, false));
            for (VocabularyCatalogEntry entry : extraCatalogEntries) {
                candidateMap.put(entry.getNormalizedTerm(), entry);
            }
        }

        int coreCount = LearningConstants.ZERO;
        int extendedCount = LearningConstants.ZERO;
        int supplementaryCount = LearningConstants.ZERO;
        int sortOrder = LearningConstants.FIRST_SEQUENCE;
        List<LearningPlanUnitEntry> unitEntriesToInsert = new ArrayList<>(words.size());
        for (JsonNode word : words) {
            String term = requiredText(word, "term", "word");
            String normalizedTerm = normalize(term);
            VocabularyCatalogEntry source = candidateMap.get(normalizedTerm);
            String tier = normalizeTier(text(word, "tier"));
            String requirement = normalizeRequirement(text(word, "mastery_requirement", "masteryRequirement"));
            LearningWordProgress before = progressService.find(userId, normalizedTerm);
            boolean firstLearning = LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                    && (before == null
                    || LearningConstants.ScenePlan.PROGRESS_UNSEEN.equals(before.getLearningState())
                    || LearningConstants.ScenePlan.PROGRESS_EXPOSED.equals(before.getLearningState()));
            LearningWordProgress progress = startImmediately
                    ? progressService.recordSceneExposure(
                            userId, term, requirement, tier, plan.getId(), unit.getId())
                    : progressService.getOrCreate(userId, term, requirement);
            LearningWordbookEntry wordbookEntry = ensureWordbookEntry(plan, source, progress, term, normalizedTerm, tier, now);

            JsonNode question = node(word, "meaning_question", "meaningQuestion", "assessment");
            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)
                    || LearningConstants.ScenePlan.TIER_REVIEW.equals(tier)) {
                validateMeaningQuestion(term, question);
            }
            List<String> acceptedSpellings = acceptedSpellings(word, term);
            LearningPlanUnitEntry unitEntry = new LearningPlanUnitEntry();
            unitEntry.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            unitEntry.setCreateBy(userId);
            unitEntry.setUpdateBy(userId);
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
            unitEntry.setVersion(LearningConstants.ZERO);
            unitEntry.setCreateTime(now);
            unitEntry.setUpdateTime(now);
            unitEntriesToInsert.add(unitEntry);

            if (LearningConstants.ScenePlan.TIER_CORE.equals(tier)) {
                coreCount++;
            } else if (LearningConstants.ScenePlan.TIER_SUPPLEMENTARY.equals(tier)) {
                supplementaryCount++;
            } else {
                extendedCount++;
            }
        }

        if (!unitEntriesToInsert.isEmpty()) {
            unitEntryMapper.insertBatch(unitEntriesToInsert);
        }

        unit.setCoreWordCount(coreCount);
        unit.setExtendedWordCount(extendedCount);
        unit.setSupplementaryWordCount(supplementaryCount);
        unit.setSceneMaterialId(material.getId());
        unit.setUpdateTime(now);
        unitMapper.updateById(unit);
        if (startImmediately) {
            plan.setCurrentUnitId(unit.getId());
        }
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
     * 在多个已生成场景之间切换当前学习单元。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse startUnit(Long userId, Long planId, Long unitId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (!LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "只有学习中的计划可以开始场景");
        }
        LearningPlanUnit unit = requireUnit(plan, unitId);
        if (LearningConstants.ScenePlan.UNIT_COMPLETED.equals(unit.getStatus())) {
            return detail(userId, planId);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean firstStart = unit.getStartedTime() == null;
        if (plan.getCurrentUnitId() != null && !Objects.equals(plan.getCurrentUnitId(), unitId)) {
            LearningPlanUnit current = unitMapper.selectById(plan.getCurrentUnitId());
            if (current != null && LearningConstants.ScenePlan.UNIT_IN_PROGRESS.equals(current.getStatus())) {
                current.setStatus(LearningConstants.ScenePlan.UNIT_READY);
                current.setUpdateTime(now);
                unitMapper.updateById(current);
            }
        }
        unit.setStatus(LearningConstants.ScenePlan.UNIT_IN_PROGRESS);
        if (firstStart) {
            unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                            .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                            .eq(LearningPlanUnitEntry::getDeleted, false))
                    .forEach(entry -> progressService.recordSceneExposure(
                            userId, entry.getTerm(), entry.getMasteryRequirement(), entry.getTier(), planId, unitId));
            unit.setStartedTime(now);
        }
        unit.setUpdateTime(now);
        unitMapper.updateById(unit);
        plan.setCurrentUnitId(unit.getId());
        plan.setUpdateTime(now);
        planMapper.updateById(plan);
        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "切换场景学习单元",
                plan.getName() + " / " + unit.getTitle());
        log.info("用户「{}」开始学习计划「{}」中的场景「{}」",
                userDisplayNameService.userName(userId), plan.getName(), unit.getTitle());
        return detail(userId, planId);
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
            if (Objects.equals(plan.getCurrentUnitId(), unit.getId()) || plan.getCurrentUnitId() == null) {
                LearningPlanUnit nextUnit = findNextIncompleteUnit(plan.getId(), unit.getId());
                plan.setCurrentUnitId(nextUnit == null ? null : nextUnit.getId());
                if (nextUnit != null && !LearningConstants.ScenePlan.UNIT_IN_PROGRESS.equals(nextUnit.getStatus())) {
                    nextUnit.setStatus(LearningConstants.ScenePlan.UNIT_IN_PROGRESS);
                    if (nextUnit.getStartedTime() == null) {
                        nextUnit.setStartedTime(now);
                    }
                    nextUnit.setUpdateTime(now);
                    unitMapper.updateById(nextUnit);
                }
            }
            if (nextCandidates(plan, targetWordCount(plan)).isEmpty() && !hasIncompleteUnit(plan.getId())) {
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
                                            List<VocabularyCatalogEntry> candidates, int targetWordCount,
                                            Long modelConfigId) {
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
        variables.put("target_word_count", targetWordCount);

        AgentChatRequest request = new AgentChatRequest();
        request.setInvocationScene(AiInvocationScene.VOCABULARY_SCENE_UNIT);
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

    private int targetWordCount(LearningPlan plan) {
        int target = LearningConstants.ScenePlan.MIN_CORE_WORDS;
        if (plan.getEndTime() != null) {
            LocalDate today = LocalDate.now();
            LocalDate planStart = plan.getStartTime() != null ? plan.getStartTime().toLocalDate() : today;
            LocalDate planEnd = plan.getEndTime().toLocalDate();
            LocalDate startForRemaining = today.isAfter(planStart) ? today : planStart;
            long remainingDays = java.time.temporal.ChronoUnit.DAYS.between(startForRemaining, planEnd) + 1;
            if (remainingDays > 0) {
                int generatedCoreCount = unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                                .eq(LearningPlanUnit::getPlanId, plan.getId())
                                .eq(LearningPlanUnit::getDeleted, false))
                        .stream()
                        .mapToInt(unit -> value(unit.getCoreWordCount()))
                        .sum();
                int remainingToGenerate = Math.max(0, value(plan.getTotalCatalogWords()) - generatedCoreCount);
                target = (int) Math.ceil((double) remainingToGenerate / remainingDays);
            }
        } else {
            LearningPlanUnit latestUnit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                    .eq(LearningPlanUnit::getPlanId, plan.getId())
                    .eq(LearningPlanUnit::getDeleted, false)
                    .orderByDesc(LearningPlanUnit::getUnitNo)
                    .last(LearningConstants.SQL_LIMIT_ONE));
            if (latestUnit != null) {
                target = value(latestUnit.getCoreWordCount());
            }
        }
        return Math.max(LearningConstants.ScenePlan.MIN_CORE_WORDS, target);
    }

    private List<VocabularyCatalogEntry> nextCandidates(LearningPlan plan, int requestedLimit) {
        Set<Long> arranged = unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getPlanId, plan.getId())
                        .isNotNull(LearningPlanUnitEntry::getCatalogEntryId)
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
        int candidateLimit = Math.max(LearningConstants.SEQUENCE_STEP, requestedLimit);
        for (VocabularyCatalogEntry entry : all) {
            if (arranged.contains(entry.getId())) {
                continue;
            }
            LearningWordProgress progress = progressService.find(plan.getUserId(), entry.getNormalizedTerm());
            if (progress != null && LearningConstants.ScenePlan.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                continue;
            }
            result.add(entry);
            if (result.size() >= candidateLimit) {
                break;
            }
        }
        return result;
    }

    private List<JsonNode> validateSceneWords(JsonNode scene, List<VocabularyCatalogEntry> candidates,
                                              int targetWordCount) {
        JsonNode vocabulary = node(scene, "vocabulary", "words");
        if (vocabulary == null || !vocabulary.isArray() || vocabulary.isEmpty()) {
            throw sceneInvalid("AI 场景结果缺少 vocabulary 数组");
        }
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
                coreCount++;
            }
            result.add(word);
        }
        int requiredMinimum = Math.min(LearningConstants.ScenePlan.MIN_CORE_WORDS, candidates.size());
        if (coreCount < requiredMinimum) {
            throw sceneInvalid("核心词数量不足 " + requiredMinimum + " 个，实际为 " + coreCount + " 个");
        }
        if (coreCount > LearningConstants.ScenePlan.MAX_CORE_WORDS_PER_UNIT) {
            throw sceneInvalid("单篇场景材料最多包含 "
                    + LearningConstants.ScenePlan.MAX_CORE_WORDS_PER_UNIT + " 个待挑战词，实际为 " + coreCount + " 个");
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
        response.setStartTime(plan.getStartTime());
        response.setEndTime(plan.getEndTime());
        response.setStatus(plan.getStatus());
        response.setTotalCatalogWords(plan.getTotalCatalogWords());
        response.setLearnedCoreWords(plan.getLearnedCoreWords());
        response.setCompletedUnitCount(plan.getCompletedUnitCount());
        response.setCurrentUnitId(plan.getCurrentUnitId());
        response.setAiSessionId(plan.getAiSessionId());
        response.setCanGenerateNext(LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus()));
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

    private LocalDate resolveRecommendedDate(LearningPlan plan, LocalDate requested, LocalDate today) {
        LocalDate resolved = requested == null ? today : requested;
        if (plan.getStartTime() != null && resolved.isBefore(plan.getStartTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "场景日期不能早于学习计划开始日期");
        }
        if (plan.getEndTime() != null && resolved.isAfter(plan.getEndTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "场景日期不能晚于学习计划结束日期");
        }
        return resolved;
    }

    private int pendingCoreCount(LearningPlanUnitResponse unit) {
        if (unit.getWords() == null || unit.getWords().isEmpty()) {
            return Math.max(LearningConstants.ZERO,
                    value(unit.getCoreWordCount()) - value(unit.getCompletedCoreCount()));
        }
        return (int) unit.getWords().stream()
                .filter(word -> LearningConstants.ScenePlan.TIER_CORE.equals(word.getTier()))
                .filter(word -> {
                    int required = LearningConstants.ScenePlan.MASTERY_SPELLING.equals(word.getMasteryRequirement())
                            ? 3 : 1;
                    return word.getPassedAssessments() == null || word.getPassedAssessments().size() < required;
                })
                .count();
    }

    private LearningPlanUnit findNextIncompleteUnit(Long planId, Long excludedUnitId) {
        return unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .ne(excludedUnitId != null, LearningPlanUnit::getId, excludedUnitId)
                .ne(LearningPlanUnit::getStatus, LearningConstants.ScenePlan.UNIT_COMPLETED)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getUnitNo)
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private boolean hasIncompleteUnit(Long planId) {
        return unitMapper.selectCount(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .ne(LearningPlanUnit::getStatus, LearningConstants.ScenePlan.UNIT_COMPLETED)
                .eq(LearningPlanUnit::getDeleted, false)) > LearningConstants.ZERO;
    }

    private void markPlanCompleted(LearningPlan plan) {
        plan.setStatus(LearningConstants.ScenePlan.STATUS_COMPLETED);
        plan.setCurrentUnitId(null);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
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

    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse pause(Long userId, Long planId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (!LearningConstants.ScenePlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "只有进行中的计划才可以暂停");
        }
        plan.setStatus(LearningConstants.ScenePlan.STATUS_PAUSED);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "暂停场景学习计划", plan.getName());
        log.info("用户「{}」暂停了场景学习计划「{}」", userDisplayNameService.userName(userId), plan.getName());
        return detail(userId, planId);
    }

    public LearningPlanResponse resume(Long userId, Long planId) {
        LearningPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> {
            LearningPlan txPlan = requirePlan(userId, planId);
            if (!LearningConstants.ScenePlan.STATUS_PAUSED.equals(txPlan.getStatus())
                    && !LearningConstants.ScenePlan.STATUS_NOT_STARTED.equals(txPlan.getStatus())) {
                throw LearningAssistantException.badRequest(
                        LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                        "只有暂停或未开始的计划才可以恢复/启动");
            }
            txPlan.setStatus(LearningConstants.ScenePlan.STATUS_ACTIVE);
            txPlan.setUpdateTime(LocalDateTime.now());
            planMapper.updateById(txPlan);
            systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "恢复场景学习计划", txPlan.getName());
            log.info("用户「{}」恢复了场景学习计划「{}」", userDisplayNameService.userName(userId), txPlan.getName());
            return txPlan;
        }));

        // 如果未开始的计划首次启动，且当前无生成单元，则在事务提交后生成第一个单元。
        if (plan.getCurrentUnitId() == null) {
            try {
                generateNextUnit(userId, planId, null, null);
            } catch (RuntimeException ex) {
                log.warn("计划已恢复，但自动生成首个场景失败 planId={} error={}", planId, ex.getMessage());
            }
        }
        return detail(userId, planId);
    }

    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse cancel(Long userId, Long planId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (LearningConstants.ScenePlan.STATUS_COMPLETED.equals(plan.getStatus())
                || LearningConstants.ScenePlan.STATUS_CANCELLED.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "已完成或已取消的计划无法取消");
        }
        plan.setStatus(LearningConstants.ScenePlan.STATUS_CANCELLED);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "取消场景学习计划", plan.getName());
        log.info("用户「{}」取消了场景学习计划「{}」", userDisplayNameService.userName(userId), plan.getName());
        return detail(userId, planId);
    }

    private record PlanUpdateResult(LearningPlan plan, boolean generateFirstUnit) {
    }

    private record CandidateWord(Integer sourceOrder, String term, String phonetic, String meaning) {
    }
}
