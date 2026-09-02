package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.learning.domain.bo.LearningAssessmentContextBO;
import com.chandler.learning.agent.learning.domain.enums.ReviewResult;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.learning.api.request.LearningAssessmentSubmitRequest;
import com.chandler.learning.agent.learning.api.response.LearningAssessmentSubmitResponse;
import com.chandler.learning.agent.learning.api.request.LearningPlanCreateRequest;
import com.chandler.learning.agent.learning.api.response.LearningPlanCalendarDayResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.request.LearningPlanUpdateRequest;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalog;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogVersion;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.AiAsyncTaskCancelledException;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.ReviewConstants;
import com.chandler.learning.agent.learning.domain.bo.PreparedUnitGroup;
import com.chandler.learning.agent.learning.domain.bo.PreparedVocabularyBatch;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 按需生成并推进场景学习单元，不设置每日场景或拼写词数量限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPlanService {


    private final LearningPlanMapper planMapper;
    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningReviewRecordMapper reviewRecordMapper;
    private final VocabularyCatalogQueryService catalogQueryService;
    private final LearningWordProgressService progressService;
    private final WordbookService wordbookService;
    private final ReviewSchedulePolicy reviewSchedulePolicy;
    private final AiAsyncTaskService aiAsyncTaskService;
    private final AiTaskExecutionService executionService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final LearningPlanResponseAssembler responseAssembler;
    private final LearningPlanVocabularySelector vocabularySelector;
    private final LearningPlanCalendarService calendarService;
    private final LearningPlanLifecycleService lifecycleService;
    private final LearningPlanSceneContentService sceneContentService;
    private final LearningPlanAssessmentSupport assessmentSupport;
    private final LearningPlanScenePersistenceService scenePersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建自助学习计划；默认立即生成第一个场景单元。
     */
    public LearningPlanResponse create(Long userId, LearningPlanCreateRequest request) {
        LearningPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> createPlan(userId, request)));
        if (ScenePlanConstants.STATUS_ACTIVE.equals(plan.getStatus())
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
        int totalWords = catalogQueryService.countPublishedEntries(version.getId());
        if (totalWords == CommonConstants.ZERO) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_NO_WORDS,
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
            plan.setStatus(ScenePlanConstants.STATUS_NOT_STARTED);
        } else {
            plan.setStatus(ScenePlanConstants.STATUS_ACTIVE);
        }
        plan.setTotalCatalogWords(totalWords);
        plan.setLearnedCoreWords(CommonConstants.ZERO);
        plan.setCompletedUnitCount(CommonConstants.ZERO);
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

    /** 更新场景学习计划。 */
    public LearningPlanResponse update(Long userId, Long planId, LearningPlanUpdateRequest request) {
        boolean generateFirstUnit = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                lifecycleService.update(userId, requirePlan(userId, planId), request).generateFirstUnit()));
        if (generateFirstUnit) {
            try {
                generateNextUnit(userId, planId, request.getModelConfigId(), null);
            } catch (RuntimeException ex) {
                log.warn("计划状态已更新，但自动生成首个场景失败 planId={} error={}", planId, ex.getMessage());
            }
        }
        return detail(userId, planId);
    }

    /** 查询列表场景学习计划。 */
    public List<LearningPlanResponse> list(Long userId) {
        return planMapper.selectList(new LambdaQueryWrapper<LearningPlan>()
                        .eq(LearningPlan::getUserId, userId)
                        .eq(LearningPlan::getDeleted, false)
                        .orderByDesc(LearningPlan::getUpdateTime))
                .stream()
                .map(plan -> responseAssembler.toPlanResponse(plan, false))
                .toList();
    }

    /** 查询详情场景学习计划。 */
    public LearningPlanResponse detail(Long userId, Long planId) {
        return responseAssembler.toPlanResponse(requirePlan(userId, planId), false);
    }

    /**
     * 按场景单元加载完整学习内容。权限校验完成后，只装配一个单元的大字段和学习状态。
     */
    public LearningPlanUnitResponse unitDetail(Long userId, Long planId, Long unitId) {
        LearningPlan plan = requirePlan(userId, planId);
        return responseAssembler.toUnitResponse(requireUnit(plan, unitId));
    }

    /** 查询指定计划在某一建议学习日期的所有场景单元实体。 */
    public List<LearningPlanUnit> findUnitsByDate(Long planId, LocalDate recommendedDate) {
        if (planId == null || recommendedDate == null) {
            return List.of();
        }
        return unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .eq(LearningPlanUnit::getRecommendedDate, recommendedDate)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getUnitNo));
    }

    /** 按单元 ID 列表查询场景单元实体。 */
    public List<LearningPlanUnit> findUnitsByIds(Long planId, List<Long> unitIds) {
        if (planId == null || unitIds == null || unitIds.isEmpty()) {
            return List.of();
        }
        return unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .in(LearningPlanUnit::getId, unitIds)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getUnitNo));
    }

    /**
     * 查询计划在指定日期范围内的日历汇总，过去日期也会返回，便于查看遗漏任务。
     */
    public List<LearningPlanCalendarDayResponse> calendar(Long userId, Long planId,
                                                          LocalDate from, LocalDate to) {
        return calendarService.calendar(userId, planId, from, to);
    }

    /**
     * 按学习计划生成指定日期的场景材料。每日目标超过 50 词时均分为多篇材料。
     */
    public List<LearningPlanUnitResponse> generateNextUnit(Long userId, Long planId, Long modelConfigId,
                                                          LocalDate recommendedDate) {
        return generateNextUnit(userId, planId, modelConfigId, recommendedDate, null);
    }

    /** 异步生成入口在每篇材料边界检查取消状态，并委托 3 步流水线逻辑。 */
    public List<LearningPlanUnitResponse> generateNextUnit(Long userId, Long planId, Long modelConfigId,
                                                          LocalDate recommendedDate, Long asyncTaskId) {
        return generateMaterialForTask(userId, planId, modelConfigId, recommendedDate, asyncTaskId);
    }

    /**
     * 重新生成指定日期的场景材料。学习单元、核心词归属和学习记录保持不变，
     * 新结果作为材料新版本发布，旧版本保留用于笔记和历史追溯。
     */
    public List<LearningPlanUnitResponse> regenerateDayUnits(Long userId, Long planId, Long modelConfigId,
                                                             LocalDate recommendedDate) {
        LearningPlan plan = requirePlan(userId, planId);
        if (ScenePlanConstants.STATUS_COMPLETED.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_COMPLETED,
                    "学习计划已经完成");
        }
        if (recommendedDate == null) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "请指定要重新生成的日期");
        }
        String lockToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int claimed = planMapper.claimGenerationLock(planId, lockToken, now,
                now.plusMinutes(ScenePlanConstants.GENERATION_LOCK_MINUTES));
        if (claimed == CommonConstants.ZERO) {
            throw LearningAssistantException.of(
                    LearningErrorCode.LEARNING_PLAN_GENERATION_IN_PROGRESS);
        }
        try {
            return regenerateDayUnitsWithLock(userId, plan, modelConfigId, recommendedDate, lockToken);
        } finally {
            planMapper.releaseGenerationLock(planId, lockToken);
        }
    }

    private List<LearningPlanUnitResponse> regenerateDayUnitsWithLock(Long userId, LearningPlan plan,
                                                                     Long modelConfigId, LocalDate recommendedDate,
                                                                     String lockToken) {
        LocalDate resolvedRecommendedDate = resolveRecommendedDate(plan, recommendedDate, LocalDate.now());

        List<LearningPlanUnit> existingUnits = unitMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnit>()
                        .eq(LearningPlanUnit::getPlanId, plan.getId())
                        .eq(LearningPlanUnit::getRecommendedDate, resolvedRecommendedDate)
                        .eq(LearningPlanUnit::getDeleted, false)
                        .orderByAsc(LearningPlanUnit::getUnitNo));

        if (existingUnits.isEmpty()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "指定日期没有可重新生成的场景材料");
        }
        List<LearningPlanUnitResponse> generatedUnits = new ArrayList<>();
        for (LearningPlanUnit unit : existingUnits) {
            renewGenerationLock(plan.getId(), lockToken);
            generatedUnits.add(regenerateUnitVersion(userId, plan, unit, modelConfigId));
        }

        systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "重新生成场景学习材料",
                plan.getName() + "（" + resolvedRecommendedDate + "）");
        log.info("用户「{}」重新生成了场景计划「{}」在「{}」的材料，共 {} 篇",
                userDisplayNameService.userName(userId), plan.getName(), resolvedRecommendedDate, generatedUnits.size());

        return List.copyOf(generatedUnits);
    }

    private LearningPlanUnitResponse regenerateUnitVersion(Long userId, LearningPlan plan,
                                                           LearningPlanUnit unit, Long modelConfigId) {
        List<LearningPlanUnitEntry> entries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                        .in(LearningPlanUnitEntry::getTier, List.of(
                                ScenePlanConstants.TIER_CORE,
                                ScenePlanConstants.TIER_REVIEW))
                        .eq(LearningPlanUnitEntry::getDeleted, false)
                        .orderByAsc(LearningPlanUnitEntry::getSortOrder));
        List<LearningPlanSceneContentService.SceneCandidate> coreWords = entries.stream()
                .filter(entry -> ScenePlanConstants.TIER_CORE.equals(entry.getTier()))
                .map(entry -> new LearningPlanSceneContentService.SceneCandidate(
                        entry.getTerm(), entry.getPhonetic(), entry.getMeaningText()))
                .toList();
        List<LearningPlanSceneContentService.SceneCandidate> reviewWords = entries.stream()
                .filter(entry -> ScenePlanConstants.TIER_REVIEW.equals(entry.getTier()))
                .map(entry -> new LearningPlanSceneContentService.SceneCandidate(
                        entry.getTerm(), entry.getPhonetic(), entry.getMeaningText()))
                .toList();
        if (coreWords.isEmpty()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "场景核心词为空，无法保持原词组重新生成");
        }
        AgentChatResponse aiResponse = sceneContentService.generateSceneWithWords(
                plan, unit.getUnitNo(), coreWords, reviewWords,
                coreWords.size(), modelConfigId);
        JsonNode scene = aiResponse.requireStructuredRoot(AiInvocationScene.VOCABULARY_SCENE_UNIT);
        List<JsonNode> words = sceneContentService.validateSceneWords(scene,
                coreWords.stream().map(LearningPlanSceneContentService.SceneCandidate::term).collect(Collectors.toSet()),
                reviewWords.stream().map(LearningPlanSceneContentService.SceneCandidate::term).collect(Collectors.toSet()),
                coreWords.size());
        return Objects.requireNonNull(transactionTemplate.execute(status -> scenePersistenceService.switchMaterialVersion(
                userId, unit, entries, aiResponse, scene, words)));
    }

    /** 步骤 1：加极短事务锁选出词组，写入步骤 Checkpoint 锁定并立即释放锁（< 5ms）。 */
    public PreparedVocabularyBatch prepareVocabularyForTask(Long userId, Long planId,
                                                           LocalDate recommendedDate, Long taskId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (ScenePlanConstants.STATUS_COMPLETED.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_COMPLETED,
                    "学习计划已经完成");
        }
        LocalDate today = LocalDate.now();
        if (plan.getStartTime() != null && today.isBefore(plan.getStartTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "学习计划尚未开始，暂不可生成场景");
        }
        if (plan.getEndTime() != null && today.isAfter(plan.getEndTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "学习计划已超出结束日期，不可继续生成场景");
        }

        LocalDate resolvedRecommendedDate = resolveRecommendedDate(plan, recommendedDate, today);
        String lockToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int claimed = planMapper.claimGenerationLock(planId, lockToken, now,
                now.plusMinutes(ScenePlanConstants.GENERATION_LOCK_MINUTES));
        if (claimed == CommonConstants.ZERO) {
            throw LearningAssistantException.of(
                    LearningErrorCode.LEARNING_PLAN_GENERATION_IN_PROGRESS);
        }
        try {
            int dailyTarget = targetWordCount(plan);
            List<VocabularyCatalogEntry> reviewWords = vocabularySelector.pendingReviewWords(plan, dailyTarget);
            List<VocabularyCatalogEntry> candidates = vocabularySelector.nextCandidates(plan, dailyTarget, reviewWords, taskId);
            if (candidates.isEmpty()) {
                if (hasIncompleteUnit(plan.getId())) {
                    throw LearningAssistantException.badRequest(
                            LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                            "词表中的词已经全部安排到场景中，请完成已生成的待学习场景");
                }
                markPlanCompleted(plan);
                throw LearningAssistantException.badRequest(
                        LearningErrorCode.LEARNING_PLAN_COMPLETED,
                        "词表中的词已经全部安排到场景中");
            }
            int totalToGenerate = Math.min(dailyTarget, candidates.size());
            List<Integer> materialWordCounts = splitMaterialWordCounts(totalToGenerate);
            List<PreparedUnitGroup> groups = new ArrayList<>();
            int candidateOffset = 0;
            for (int i = 0; i < materialWordCounts.size(); i++) {
                int batchSize = materialWordCounts.get(i);
                List<Long> candidateIds = candidates.subList(candidateOffset, candidateOffset + batchSize)
                        .stream().map(VocabularyCatalogEntry::getId).toList();
                int reviewStart = reviewWords.size() * i / materialWordCounts.size();
                int reviewEnd = reviewWords.size() * (i + 1) / materialWordCounts.size();
                List<Long> reviewIds = reviewWords.subList(reviewStart, reviewEnd)
                        .stream().map(VocabularyCatalogEntry::getId).toList();
                groups.add(new PreparedUnitGroup(i, candidateIds, reviewIds, batchSize));
                candidateOffset += batchSize;
            }
            PreparedVocabularyBatch batch = new PreparedVocabularyBatch(planId, resolvedRecommendedDate,
                    totalToGenerate, groups);
            if (taskId != null) {
                executionService.saveStepCheckpoint(taskId, "prepare_vocabulary", batch);
            }
            return batch;
        } finally {
            planMapper.releaseGenerationLock(planId, lockToken);
        }
    }

    /** 步骤 2：读取 Checkpoint 词组（若有冲突自动重选），无锁调用 AI 撰写故事并正式落库场景材料。 */
    public List<LearningPlanUnitResponse> generateMaterialForTask(Long userId, Long planId,
                                                                 Long modelConfigId,
                                                                 LocalDate recommendedDate,
                                                                 Long taskId) {
        LearningPlan plan = requirePlan(userId, planId);
        PreparedVocabularyBatch batch = null;
        if (taskId != null) {
            batch = executionService.getStepCheckpoint(taskId, "prepare_vocabulary", PreparedVocabularyBatch.class);
        }
        if (batch == null) {
            batch = prepareVocabularyForTask(userId, planId, recommendedDate, taskId);
        } else {
            // 校验 Checkpoint 中的词条是否已被其他已落库场景占用
            Set<Long> arrangedInDb = unitEntryMapper.selectList(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                            .eq(LearningPlanUnitEntry::getPlanId, planId)
                            .isNotNull(LearningPlanUnitEntry::getCatalogEntryId)
                            .eq(LearningPlanUnitEntry::getDeleted, false))
                    .stream().map(LearningPlanUnitEntry::getCatalogEntryId).collect(Collectors.toSet());
            boolean conflict = batch.allCandidateEntryIds().stream().anyMatch(arrangedInDb::contains);
            if (conflict) {
                log.info("检测到任务检查点中的词条已被其他场景占用，自动重新分配词组 taskId={} planId={}", taskId, planId);
                batch = prepareVocabularyForTask(userId, planId, recommendedDate, taskId);
            }
        }

        LocalDate today = LocalDate.now();
        LocalDate resolvedRecommendedDate = batch.getRecommendedDate() != null
                ? batch.getRecommendedDate()
                : resolveRecommendedDate(plan, recommendedDate, today);

        List<Long> allEntryIds = new ArrayList<>(batch.allCandidateEntryIds());
        for (PreparedUnitGroup group : batch.getUnitGroups()) {
            if (group.getReviewEntryIds() != null) {
                allEntryIds.addAll(group.getReviewEntryIds());
            }
        }
        Map<Long, VocabularyCatalogEntry> entryMap = catalogQueryService.findEntries(allEntryIds)
                .stream().collect(Collectors.toMap(VocabularyCatalogEntry::getId, e -> e, (a, b) -> a));

        List<LearningPlanUnitResponse> generatedUnits = new ArrayList<>();
        for (PreparedUnitGroup group : batch.getUnitGroups()) {
            ensureAsyncTaskActive(taskId);
            List<VocabularyCatalogEntry> candidates = group.getCandidateEntryIds().stream()
                    .map(entryMap::get).filter(Objects::nonNull).toList();
            List<VocabularyCatalogEntry> reviewWords = group.getReviewEntryIds() == null ? List.of()
                    : group.getReviewEntryIds().stream().map(entryMap::get).filter(Objects::nonNull).toList();
            generatedUnits.add(generateSingleUnit(userId, plan, modelConfigId, resolvedRecommendedDate,
                    today, candidates, group.getTargetCount(), reviewWords, taskId));
        }
        return List.copyOf(generatedUnits);
    }

    static List<Integer> splitMaterialWordCounts(int totalWordCount) {
        if (totalWordCount <= CommonConstants.ZERO) {
            return List.of();
        }
        int materialCount = (totalWordCount + ScenePlanConstants.MAX_CORE_WORDS_PER_UNIT - 1)
                / ScenePlanConstants.MAX_CORE_WORDS_PER_UNIT;
        int baseSize = totalWordCount / materialCount;
        int remainder = totalWordCount % materialCount;
        List<Integer> result = new ArrayList<>(materialCount);
        for (int index = CommonConstants.ZERO; index < materialCount; index++) {
            result.add(baseSize + (index < remainder ? CommonConstants.SEQUENCE_STEP : CommonConstants.ZERO));
        }
        return List.copyOf(result);
    }

    private LearningPlanUnitResponse generateSingleUnit(Long userId, LearningPlan plan, Long modelConfigId,
                                                        LocalDate resolvedRecommendedDate, LocalDate today,
                                                        List<VocabularyCatalogEntry> candidates, int targetWordCount,
                                                        List<VocabularyCatalogEntry> reviewWords,
                                                        Long asyncTaskId) {
        ensureAsyncTaskActive(asyncTaskId);
        int unitNo = nextUnitNo(plan.getId());
        AgentChatResponse aiResponse = sceneContentService.generateScene(plan, unitNo, candidates, reviewWords,
                targetWordCount, modelConfigId);
        ensureAsyncTaskActive(asyncTaskId);
        JsonNode scene = aiResponse.requireStructuredRoot(AiInvocationScene.VOCABULARY_SCENE_UNIT);
        List<JsonNode> words = sceneContentService.validateSceneWords(scene, candidates, reviewWords, targetWordCount);
        ensureAsyncTaskActive(asyncTaskId);
        return Objects.requireNonNull(transactionTemplate.execute(status -> scenePersistenceService.persistGeneratedUnit(
                userId, plan, resolvedRecommendedDate, today, candidates, reviewWords,
                unitNo, aiResponse, scene, words)));
    }

    private void ensureAsyncTaskActive(Long asyncTaskId) {
        if (asyncTaskId != null && aiAsyncTaskService.isCancelled(asyncTaskId)) {
            throw new AiAsyncTaskCancelledException();
        }
    }

    private void renewGenerationLock(Long planId, String lockToken) {
        int renewed = planMapper.renewGenerationLock(planId, lockToken,
                LocalDateTime.now().plusMinutes(ScenePlanConstants.GENERATION_LOCK_MINUTES));
        if (renewed == CommonConstants.ZERO) {
            throw LearningAssistantException.of(
                    LearningErrorCode.LEARNING_PLAN_GENERATION_IN_PROGRESS);
        }
    }

    /**
     * 在多个已生成场景之间切换当前学习单元。
     * <p>
     * 通过 Spring Event + 异步线程池持久化单元状态切换和审计日志，
     * 接口主线程无需等待任何写库和锁竞争，实现 0 毫秒级极速响应。
     */
    public LearningPlanResponse startUnit(Long userId, Long planId, Long unitId) {
        LearningPlan plan = requirePlan(userId, planId);
        if (!ScenePlanConstants.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "只有学习中的计划可以开始场景");
        }
        LearningPlanUnit unit = requireUnit(plan, unitId);
        if (ScenePlanConstants.UNIT_COMPLETED.equals(unit.getStatus())) {
            plan.setCurrentUnitId(unit.getId());
            return responseAssembler.toPlanResponse(plan, false);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean firstStart = unit.getStartedTime() == null;
        Long previousUnitId = plan.getCurrentUnitId();

        // 内存中即时设置当前单元指针，构造极速响应
        plan.setCurrentUnitId(unit.getId());
        plan.setUpdateTime(now);

        // 发布领域事件，由异步线程池执行单元状态切换与系统审计日志持久化
        String traceId = org.slf4j.MDC.get("TraceId");
        eventPublisher.publishEvent(new LearningUnitStartedEvent(
                userId, planId, unitId, previousUnitId, firstStart, now,
                plan.getName(), unit.getTitle(), traceId));

        return responseAssembler.toPlanResponse(plan, false);
    }

    /**
     * 提交四选一、跟敲或按含义拼写结果。
     * 采用 5 合 1 高性能联表查询 + 增量完成数判断 + 答题流水/日志异步化。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningAssessmentSubmitResponse submitAssessment(Long userId, Long planId, Long unitId,
                                                             LearningAssessmentSubmitRequest request) {
        LearningAssessmentContextBO ctx = unitEntryMapper.selectAssessmentContext(
                userId, planId, unitId, request.getUnitEntryId());
        if (ctx == null) {
            throw assessmentInvalid("学习计划或词条不存在");
        }
        if (ctx.getWordbookEntryId() == null) {
            throw assessmentInvalid("该词当前仅用于场景展示，提升为核心词后才能参加检查");
        }
        String type = assessmentSupport.normalizeAssessmentType(request.getAssessmentType());
        if (!ScenePlanConstants.MASTERY_SPELLING.equals(ctx.getMasteryRequirement())
                && !ScenePlanConstants.ASSESSMENT_MEANING_CHOICE.equals(type)) {
            throw assessmentInvalid("该词的掌握要求是认识，不需要拼写检查");
        }

        JsonNode question = assessmentSupport.readTree(ctx.getAssessmentJson());
        String correctAnswer;
        boolean correct;
        double typingAccuracy = 100D;
        if (ScenePlanConstants.ASSESSMENT_MEANING_CHOICE.equals(type)) {
            correctAnswer = requiredText(question, "correct_answer", "correctAnswer", "answer");
            correct = assessmentSupport.normalizeAnswer(request.getAnswer()).equals(assessmentSupport.normalizeAnswer(correctAnswer));
        } else {
            List<String> accepted = assessmentSupport.readStringList(ctx.getAcceptedSpellingsJson());
            if (accepted.isEmpty()) {
                accepted = List.of(ctx.getTerm());
            }
            correctAnswer = accepted.get(0);
            String answer = assessmentSupport.normalizeSpelling(request.getAnswer());
            correct = accepted.stream().map(assessmentSupport::normalizeSpelling).anyMatch(answer::equals);
            typingAccuracy = assessmentSupport.spellingAccuracy(answer, accepted);
        }

        LocalDateTime now = LocalDateTime.now();
        ReviewResult result = ReviewResult.of(correct ? ReviewConstants.RESULT_REMEMBERED : ReviewConstants.RESULT_FORGOTTEN);

        int stageBefore = ctx.getWordbookStage() == null ? ReviewConstants.INITIAL_STAGE : ctx.getWordbookStage();
        int masteryBefore = ctx.getWordbookMastery() == null ? ReviewConstants.INITIAL_MASTERY : ctx.getWordbookMastery();
        int stageAfter = result.remembered() ? stageBefore + CommonConstants.SEQUENCE_STEP : ReviewConstants.INITIAL_STAGE;
        int masteryAfter = Math.max(ReviewConstants.MIN_MASTERY, Math.min(ReviewConstants.MAX_MASTERY,
                masteryBefore + (result.remembered() ? ReviewConstants.REMEMBERED_MASTERY_DELTA : -ReviewConstants.FORGOTTEN_MASTERY_DELTA)));

        LocalDateTime nextReviewTime = reviewSchedulePolicy.nextReviewTime(
                now, stageAfter, result.remembered(), result.vague());
        wordbookService.completeReviewState(ctx.getWordbookEntryId(), result, now, nextReviewTime);

        LearningWordProgress progress = progressService.recordAssessment(
                ctx.getWordProgressId(), type, correct, nextReviewTime);

        int completedCoreCount = value(ctx.getCompletedCoreCount());
        if (correct) {
            List<String> passedList = reviewRecordMapper.selectPassedAssessmentTypes(unitId, ctx.getWordbookEntryId());
            Set<String> passedSet = new HashSet<>(passedList);
            boolean wasCompleted = isEntryComplete(ctx.getMasteryRequirement(), passedSet);
            passedSet.add(type);
            boolean nowCompleted = isEntryComplete(ctx.getMasteryRequirement(), passedSet);
            if (!wasCompleted && nowCompleted) {
                completedCoreCount = completedCoreCount + CommonConstants.SEQUENCE_STEP;
                LearningPlanUnit unitUpdate = new LearningPlanUnit();
                unitUpdate.setId(unitId);
                unitUpdate.setCompletedCoreCount(completedCoreCount);
                unitUpdate.setUpdateTime(now);
                unitMapper.updateById(unitUpdate);
            }
        }

        LearningReviewRecord record = new LearningReviewRecord();
        record.setId(IdWorker.getId());
        record.setCreateBy(userId);
        record.setUpdateBy(userId);
        record.setUserId(userId);
        record.setWordbookId(ctx.getWordbookId());
        record.setEntryId(ctx.getWordbookEntryId());
        record.setVocabularyId(null);
        record.setWordProgressId(ctx.getWordProgressId());
        record.setPlanId(planId);
        record.setUnitId(unitId);
        record.setAssessmentType(type);
        record.setQuestionJson(ctx.getAssessmentJson());
        record.setAnswerText(request.getAnswer());
        record.setCorrectAnswer(correctAnswer);
        record.setCheckResult(correct ? ScenePlanConstants.CHECK_CORRECT : ScenePlanConstants.CHECK_INCORRECT);
        record.setTypingAccuracy(typingAccuracy);
        record.setHintLevel(request.getHintLevel());
        record.setAttemptCount(request.getAttemptCount());
        record.setDurationMillis(request.getDurationMillis());
        record.setNormalizedTerm(ctx.getNormalizedTerm());
        record.setResult(result.getCode());
        record.setScore(correct ? ReviewConstants.MAX_MASTERY : ReviewConstants.MIN_MASTERY);
        record.setReviewStageBefore(stageBefore);
        record.setReviewStageAfter(stageAfter);
        record.setMasteryBefore(masteryBefore);
        record.setMasteryAfter(masteryAfter);
        record.setNextReviewTime(nextReviewTime);
        record.setDurationSeconds(request.getDurationMillis() == null ? null : Math.toIntExact(Math.min(Integer.MAX_VALUE, request.getDurationMillis() / 1000L)));
        record.setCreateTime(now);
        record.setUpdateTime(now);

        String userName = userDisplayNameService.userName(userId);
        eventPublisher.publishEvent(new LearningAssessmentSubmittedEvent(
                userId, record, ctx.getPlanName(), ctx.getUnitTitle(),
                ctx.getTerm(), result.getLabel(), masteryBefore, masteryAfter, userName));

        LearningAssessmentSubmitResponse response = new LearningAssessmentSubmitResponse();
        response.setUnitEntryId(ctx.getUnitEntryId());
        response.setAssessmentType(type);
        response.setCorrect(correct);
        response.setCorrectAnswer(correctAnswer);
        response.setTypingAccuracy(typingAccuracy);
        response.setLearningState(progress.getLearningState());
        response.setRecognitionScore(progress.getRecognitionScore());
        response.setSpellingScore(progress.getSpellingScore());
        response.setCompletedCoreCount(completedCoreCount);
        response.setCoreWordCount(ctx.getCoreWordCount());
        response.setUnitReadyToComplete(completedCoreCount >= value(ctx.getCoreWordCount()));
        response.setNextReviewTime(nextReviewTime);
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
                    LearningErrorCode.LEARNING_PLAN_UNIT_INCOMPLETE,
                    "还有 " + (value(unit.getCoreWordCount()) - completedCore) + " 个核心词未通过本场景检查");
        }
        if (!ScenePlanConstants.UNIT_COMPLETED.equals(unit.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            unit.setStatus(ScenePlanConstants.UNIT_COMPLETED);
            unit.setCompletedTime(now);
            unit.setUpdateTime(now);
            unitMapper.updateById(unit);
            int newlyLearned = unitEntryMapper.selectCount(new LambdaQueryWrapper<LearningPlanUnitEntry>()
                    .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                    .eq(LearningPlanUnitEntry::getTier, ScenePlanConstants.TIER_CORE)
                    .eq(LearningPlanUnitEntry::getFirstLearning, true)
                    .eq(LearningPlanUnitEntry::getDeleted, false)).intValue();
            plan.setLearnedCoreWords(value(plan.getLearnedCoreWords()) + newlyLearned);
            plan.setCompletedUnitCount(value(plan.getCompletedUnitCount()) + CommonConstants.SEQUENCE_STEP);
            Long previousCurrentUnitId = plan.getCurrentUnitId();
            LearningPlanUnit nextUnit = findNextIncompleteUnit(plan.getId(), unit);
            plan.setCurrentUnitId(nextUnit == null ? null : nextUnit.getId());

            if (previousCurrentUnitId != null
                    && !Objects.equals(previousCurrentUnitId, unit.getId())
                    && (nextUnit == null || !Objects.equals(previousCurrentUnitId, nextUnit.getId()))) {
                LearningPlanUnit oldInProgress = unitMapper.selectById(previousCurrentUnitId);
                if (oldInProgress != null && ScenePlanConstants.UNIT_IN_PROGRESS.equals(oldInProgress.getStatus())) {
                    oldInProgress.setStatus(ScenePlanConstants.UNIT_READY);
                    oldInProgress.setUpdateTime(now);
                    unitMapper.updateById(oldInProgress);
                }
            }

            if (nextUnit != null && !ScenePlanConstants.UNIT_IN_PROGRESS.equals(nextUnit.getStatus())) {
                nextUnit.setStatus(ScenePlanConstants.UNIT_IN_PROGRESS);
                if (nextUnit.getStartedTime() == null) {
                    nextUnit.setStartedTime(now);
                }
                nextUnit.setUpdateTime(now);
                unitMapper.updateById(nextUnit);
            }
            if (vocabularySelector.nextCandidates(plan, targetWordCount(plan)).isEmpty()
                    && !hasIncompleteUnit(plan.getId())) {
                plan.setStatus(ScenePlanConstants.STATUS_COMPLETED);
            }
            plan.setUpdateTime(now);
            planMapper.updateById(plan);
            systemLogService.record(userId, SystemLogType.LEARNING_PLAN, "完成场景学习单元",
                    plan.getName() + " / " + unit.getTitle());
            log.info("用户「{}」完成了计划「{}」中的场景「{}」，可继续手动生成下一个场景",
                    userDisplayNameService.userName(userId), plan.getName(), unit.getTitle());
        }
        return responseAssembler.toPlanResponse(plan, false);
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
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (entry == null) {
            throw assessmentInvalid("场景词汇不存在");
        }
        if (!ScenePlanConstants.TIER_CORE.equals(entry.getTier())) {
            String previousTier = entry.getTier();
            LearningWordProgress progress = progressService.recordSceneExposure(
                    userId, entry.getTerm(), ScenePlanConstants.MASTERY_RECOGNITION,
                    ScenePlanConstants.TIER_CORE, planId, unitId);
            VocabularyCatalogEntry source = entry.getCatalogEntryId() == null
                    ? null
                    : catalogQueryService.findEntry(entry.getCatalogEntryId());
            LearningWordbookEntry wordbookEntry = wordbookService.ensureLearningEntry(
                    userId, plan.getWordbookId(), source, progress, entry.getTerm(), entry.getNormalizedTerm(),
                    true, LocalDateTime.now());
            entry.setTier(ScenePlanConstants.TIER_CORE);
            entry.setMasteryRequirement(ScenePlanConstants.MASTERY_RECOGNITION);
            entry.setWordProgressId(progress.getId());
            entry.setWordbookEntryId(wordbookEntry == null ? null : wordbookEntry.getId());
            entry.setFirstLearning(true);
            ensurePromotionAssessment(entry, unit);
            entry.setUpdateTime(LocalDateTime.now());
            unitEntryMapper.updateById(entry);
            unit.setCoreWordCount(value(unit.getCoreWordCount()) + CommonConstants.SEQUENCE_STEP);
            if (ScenePlanConstants.TIER_SUPPLEMENTARY.equals(previousTier)) {
                unit.setSupplementaryWordCount(Math.max(0, value(unit.getSupplementaryWordCount()) - 1));
            } else {
                unit.setExtendedWordCount(Math.max(0, value(unit.getExtendedWordCount()) - 1));
            }
            unit.setUpdateTime(LocalDateTime.now());
            unitMapper.updateById(unit);
        }
        return responseAssembler.toEntryResponse(entry);
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
                .filter(meaning -> !assessmentSupport.normalizeAnswer(meaning)
                        .equals(assessmentSupport.normalizeAnswer(entry.getMeaningText())))
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

    private int targetWordCount(LearningPlan plan) {
        int target = ScenePlanConstants.MIN_CORE_WORDS;
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
                    .last(CommonConstants.SQL_LIMIT_ONE));
            if (latestUnit != null) {
                target = value(latestUnit.getCoreWordCount());
            }
        }
        return Math.max(ScenePlanConstants.MIN_CORE_WORDS, target);
    }

    private boolean isEntryComplete(String masteryRequirement, Set<String> passed) {
        boolean meaningPassed = passed.contains(ScenePlanConstants.ASSESSMENT_MEANING_CHOICE);
        boolean spellingPassed = !ScenePlanConstants.MASTERY_SPELLING.equals(masteryRequirement)
                || (passed.contains(ScenePlanConstants.ASSESSMENT_COPY_TYPING)
                && passed.contains(ScenePlanConstants.ASSESSMENT_MEANING_SPELLING));
        return meaningPassed && spellingPassed;
    }

    private int refreshCompletedCoreCount(LearningPlanUnit unit) {
        List<LearningPlanUnitEntry> coreEntries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                        .eq(LearningPlanUnitEntry::getTier, ScenePlanConstants.TIER_CORE)
                        .eq(LearningPlanUnitEntry::getDeleted, false));
        if (coreEntries.isEmpty()) {
            unit.setCompletedCoreCount(0);
            return 0;
        }
        int completed = CommonConstants.ZERO;
        for (LearningPlanUnitEntry entry : coreEntries) {
            if (entry.getWordbookEntryId() == null) {
                continue;
            }
            List<String> passed = reviewRecordMapper.selectPassedAssessmentTypes(unit.getId(), entry.getWordbookEntryId());
            if (isEntryComplete(entry.getMasteryRequirement(), new HashSet<>(passed))) {
                completed++;
            }
        }
        unit.setCompletedCoreCount(completed);
        unit.setUpdateTime(LocalDateTime.now());
        unitMapper.updateById(unit);
        return completed;
    }

    private LearningPlan requirePlan(Long userId, Long planId) {
        LearningPlan plan = planMapper.selectOne(new LambdaQueryWrapper<LearningPlan>()
                .eq(LearningPlan::getId, planId)
                .eq(LearningPlan::getUserId, userId)
                .eq(LearningPlan::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (plan == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_PLAN_NOT_FOUND,
                    "学习计划不存在: " + planId);
        }
        return plan;
    }

    private LearningPlanUnit requireUnit(LearningPlan plan, Long unitId) {
        LearningPlanUnit unit = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getId, unitId)
                .eq(LearningPlanUnit::getPlanId, plan.getId())
                .eq(LearningPlanUnit::getDeleted, false)
                .last(CommonConstants.SQL_LIMIT_ONE));
        if (unit == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND,
                    "场景学习单元不存在: " + unitId);
        }
        return unit;
    }

    private LocalDate resolveRecommendedDate(LearningPlan plan, LocalDate requested, LocalDate today) {
        LocalDate resolved = requested == null ? today : requested;
        if (plan.getStartTime() != null && resolved.isBefore(plan.getStartTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "场景日期不能早于学习计划开始日期");
        }
        if (plan.getEndTime() != null && resolved.isAfter(plan.getEndTime().toLocalDate())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.LEARNING_PLAN_STATE_ERROR,
                    "场景日期不能晚于学习计划结束日期");
        }
        return resolved;
    }

    private int pendingCoreCount(LearningPlanUnitResponse unit) {
        if (unit.getWords() == null || unit.getWords().isEmpty()) {
            return Math.max(CommonConstants.ZERO,
                    value(unit.getCoreWordCount()) - value(unit.getCompletedCoreCount()));
        }
        return (int) unit.getWords().stream()
                .filter(word -> ScenePlanConstants.TIER_CORE.equals(word.getTier()))
                .filter(word -> {
                    int required = ScenePlanConstants.MASTERY_SPELLING.equals(word.getMasteryRequirement())
                            ? 3 : 1;
                    return word.getPassedAssessments() == null || word.getPassedAssessments().size() < required;
                })
                .count();
    }

    private LearningPlanUnit findNextIncompleteUnit(Long planId, LearningPlanUnit currentUnit) {
        LocalDate currentDate = currentUnit == null ? null : currentUnit.getRecommendedDate();
        Integer currentUnitNo = currentUnit == null ? null : currentUnit.getUnitNo();
        if (currentDate != null) {
            LearningPlanUnit subsequent = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                    .eq(LearningPlanUnit::getPlanId, planId)
                    .ne(LearningPlanUnit::getStatus, ScenePlanConstants.UNIT_COMPLETED)
                    .eq(LearningPlanUnit::getDeleted, false)
                    .and(wrapper -> wrapper
                            .gt(LearningPlanUnit::getRecommendedDate, currentDate)
                            .or(orWrapper -> orWrapper
                                    .eq(LearningPlanUnit::getRecommendedDate, currentDate)
                                    .gt(currentUnitNo != null, LearningPlanUnit::getUnitNo, currentUnitNo)
                            )
                    )
                    .orderByAsc(LearningPlanUnit::getRecommendedDate)
                    .orderByAsc(LearningPlanUnit::getUnitNo)
                    .last(CommonConstants.SQL_LIMIT_ONE));
            if (subsequent != null) {
                return subsequent;
            }
        } else if (currentUnitNo != null) {
            LearningPlanUnit subsequent = unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                    .eq(LearningPlanUnit::getPlanId, planId)
                    .gt(LearningPlanUnit::getUnitNo, currentUnitNo)
                    .ne(LearningPlanUnit::getStatus, ScenePlanConstants.UNIT_COMPLETED)
                    .eq(LearningPlanUnit::getDeleted, false)
                    .orderByAsc(LearningPlanUnit::getRecommendedDate)
                    .orderByAsc(LearningPlanUnit::getUnitNo)
                    .last(CommonConstants.SQL_LIMIT_ONE));
            if (subsequent != null) {
                return subsequent;
            }
        }
        Long excludedId = currentUnit == null ? null : currentUnit.getId();
        return unitMapper.selectOne(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .ne(excludedId != null, LearningPlanUnit::getId, excludedId)
                .ne(LearningPlanUnit::getStatus, ScenePlanConstants.UNIT_COMPLETED)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getRecommendedDate)
                .orderByAsc(LearningPlanUnit::getUnitNo)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    private boolean hasIncompleteUnit(Long planId) {
        return unitMapper.selectCount(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .ne(LearningPlanUnit::getStatus, ScenePlanConstants.UNIT_COMPLETED)
                .eq(LearningPlanUnit::getDeleted, false)) > CommonConstants.ZERO;
    }

    private void markPlanCompleted(LearningPlan plan) {
        plan.setStatus(ScenePlanConstants.STATUS_COMPLETED);
        plan.setCurrentUnitId(null);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    private int nextUnitNo(Long planId) {
        Integer maxUnitNo = unitMapper.selectMaxUnitNoIncludingDeleted(planId);
        return maxUnitNo == null ? CommonConstants.FIRST_SEQUENCE : maxUnitNo + 1;
    }

    private VocabularyCatalogVersion requirePublishedVersion(Long userId, Long versionId) {
        return catalogQueryService.requirePublishedVersion(userId, versionId);
    }

    private VocabularyCatalog requireCatalog(Long userId, Long catalogId) {
        return catalogQueryService.requireAccessibleCatalog(userId, catalogId);
    }

    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        return wordbookService.requireOwnedWordbook(userId, wordbookId);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "场景学习数据序列化失败",
                    ex);
        }
    }

    private int value(Integer value) {
        return value == null ? CommonConstants.ZERO : value;
    }

    private LearningAssistantException sceneInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningErrorCode.LEARNING_SCENE_PARSE_FAILED,
                message);
    }

    private LearningAssistantException assessmentInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningErrorCode.LEARNING_ASSESSMENT_INVALID,
                message);
    }

    /** 暂停场景学习计划。 */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse pause(Long userId, Long planId) {
        LearningPlan plan = requirePlan(userId, planId);
        lifecycleService.pause(userId, plan);
        return detail(userId, planId);
    }

    /** 恢复场景学习计划。 */
    public LearningPlanResponse resume(Long userId, Long planId) {
        LearningPlan plan = Objects.requireNonNull(transactionTemplate.execute(status -> {
            LearningPlan txPlan = requirePlan(userId, planId);
            lifecycleService.resume(userId, txPlan);
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

    /** 取消场景学习计划。 */
    @Transactional(rollbackFor = Exception.class)
    public LearningPlanResponse cancel(Long userId, Long planId) {
        LearningPlan plan = requirePlan(userId, planId);
        lifecycleService.cancel(userId, plan);
        return detail(userId, planId);
    }

}
