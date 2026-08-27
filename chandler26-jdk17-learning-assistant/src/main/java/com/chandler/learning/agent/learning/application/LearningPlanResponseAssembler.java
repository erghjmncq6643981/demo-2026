package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.learning.api.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitWordSummaryResponse;
import com.chandler.learning.agent.learning.api.SceneRelatedWordResponse;
import com.chandler.learning.agent.learning.domain.LearningPlan;
import com.chandler.learning.agent.learning.domain.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitEntryItem;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitItem;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitWordSummaryItem;
import com.chandler.learning.agent.learning.domain.LearningReviewRecord;
import com.chandler.learning.agent.learning.domain.LearningSceneMaterial;
import com.chandler.learning.agent.learning.domain.LearningSceneRelatedWord;
import com.chandler.learning.agent.vocabulary.domain.LearningWordProgress;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningReviewRecordMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningSceneRelatedWordMapper;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习计划响应装配器。
 * <p>
 * 计划摘要不加载场景大字段；进入具体场景后再批量加载该单元的材料、词条、进度和检测记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPlanResponseAssembler {

    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final LearningSceneRelatedWordMapper relatedWordMapper;
    private final LearningWordProgressService progressService;
    private final LearningReviewRecordMapper reviewRecordMapper;

    /** 装配计划摘要或完整详情。 */
    public LearningPlanResponse toPlanResponse(LearningPlan plan, boolean includeUnits) {
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
        response.setUnits(includeUnits ? loadUnits(plan.getId()) : List.of());
        response.setCreateTime(plan.getCreateTime());
        response.setUpdateTime(plan.getUpdateTime());
        return response;
    }

    /** 装配单个刚刚变化的单元。 */
    public LearningPlanUnitResponse toUnitResponse(LearningPlanUnit unit) {
        return toUnitResponses(List.of(unit), unit.getPlanId()).get(0);
    }

    /** 批量装配日历或计划详情中的单元。 */
    public List<LearningPlanUnitResponse> toUnitResponses(List<LearningPlanUnit> units, Long planId) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        List<Long> unitIds = units.stream().map(LearningPlanUnit::getId).toList();
        List<com.chandler.learning.agent.learning.domain.LearningPlanUnitItem> unitItems = unitMapper.selectUnitsWithMaterial(planId, unitIds);
        return loadUnitResponses(unitItems, planId);
    }

    /** 装配日历和首页使用的轻量单元摘要，不查询文章、词卡或学习记录。 */
    public List<LearningPlanUnitResponse> toUnitSummaryResponses(List<LearningPlanUnit> units) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        return units.stream().map(unit -> toUnitSummaryResponse(unit, List.of())).toList();
    }

    /**
     * 装配带待挑战词面的日历摘要。只查询词面，避免把场景材料和词卡大字段带到日历接口。
     */
    public List<LearningPlanUnitResponse> toUnitSummaryResponses(List<LearningPlanUnit> units, Long planId) {
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        List<Long> unitIds = units.stream().map(LearningPlanUnit::getId).toList();
        List<LearningPlanUnitWordSummaryItem> entries = unitEntryMapper.selectWordSummaries(planId, unitIds);
        List<Long> wordbookEntryIds = entries.stream()
                .map(LearningPlanUnitWordSummaryItem::getWordbookEntryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Set<String>> passedByEntry = wordbookEntryIds.isEmpty()
                ? Map.of()
                : reviewRecordMapper.selectList(new LambdaQueryWrapper<LearningReviewRecord>()
                                .in(LearningReviewRecord::getEntryId, wordbookEntryIds)
                                .in(LearningReviewRecord::getUnitId, unitIds)
                                .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                                .eq(LearningReviewRecord::getDeleted, false))
                        .stream()
                        .filter(record -> record.getEntryId() != null && record.getAssessmentType() != null)
                        .collect(Collectors.groupingBy(LearningReviewRecord::getEntryId,
                                Collectors.mapping(LearningReviewRecord::getAssessmentType, Collectors.toSet())));
        Map<Long, List<LearningPlanUnitWordSummaryResponse>> pendingByUnit = entries.stream()
                .filter(entry -> isPending(entry, passedByEntry))
                .collect(Collectors.groupingBy(LearningPlanUnitWordSummaryItem::getUnitId,
                        Collectors.mapping(this::toWordSummaryResponse, Collectors.toList())));
        return units.stream()
                .map(unit -> toUnitSummaryResponse(unit, pendingByUnit.getOrDefault(unit.getId(), List.of())))
                .toList();
    }

    private LearningPlanUnitResponse toUnitSummaryResponse(
            LearningPlanUnit unit, List<LearningPlanUnitWordSummaryResponse> pendingWords) {
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
        response.setSceneMaterialId(unit.getSceneMaterialId());
        response.setMaterialAvailable(unit.getSceneMaterialId() != null);
        response.setPendingChallengeWords(pendingWords);
        response.setRelatedWords(List.of());
        response.setWords(List.of());
        response.setGeneratedTime(unit.getGeneratedTime());
        response.setCompletedTime(unit.getCompletedTime());
        return response;
    }

    private boolean isPending(LearningPlanUnitWordSummaryItem entry, Map<Long, Set<String>> passedByEntry) {
        Set<String> passed = passedByEntry.getOrDefault(entry.getWordbookEntryId(), Set.of());
        boolean meaningPassed = passed.contains(LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE);
        boolean spellingPassed = !LearningConstants.ScenePlan.MASTERY_SPELLING.equals(entry.getMasteryRequirement())
                || (passed.contains(LearningConstants.ScenePlan.ASSESSMENT_COPY_TYPING)
                && passed.contains(LearningConstants.ScenePlan.ASSESSMENT_MEANING_SPELLING));
        return !(meaningPassed && spellingPassed);
    }

    private LearningPlanUnitWordSummaryResponse toWordSummaryResponse(LearningPlanUnitWordSummaryItem item) {
        LearningPlanUnitWordSummaryResponse response = new LearningPlanUnitWordSummaryResponse();
        response.setId(item.getId());
        response.setTerm(item.getTerm());
        response.setTier(item.getTier());
        response.setMasteryRequirement(item.getMasteryRequirement());
        return response;
    }

    /** 装配单个词条，适用于提升词汇等单条命令响应。 */
    public LearningPlanUnitEntryResponse toEntryResponse(LearningPlanUnitEntry entry) {
        LearningWordProgress progress = entry.getWordProgressId() == null
                ? null : progressService.findById(entry.getWordProgressId());
        Map<Long, List<String>> passedMap = entry.getWordbookEntryId() == null
                ? Map.of()
                : Map.of(entry.getWordbookEntryId(), reviewRecordMapper.selectList(
                                new LambdaQueryWrapper<LearningReviewRecord>()
                                        .eq(LearningReviewRecord::getUnitId, entry.getUnitId())
                                        .eq(LearningReviewRecord::getEntryId, entry.getWordbookEntryId())
                                        .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                                        .eq(LearningReviewRecord::getDeleted, false))
                        .stream()
                        .map(LearningReviewRecord::getAssessmentType)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        return toEntryResponse(entry, progress, passedMap);
    }

    private List<LearningPlanUnitResponse> loadUnits(Long planId) {
        List<com.chandler.learning.agent.learning.domain.LearningPlanUnitItem> unitItems = unitMapper.selectUnitsWithMaterial(planId, null);
        return loadUnitResponses(unitItems, planId);
    }

    private List<LearningPlanUnitResponse> loadUnitResponses(
            List<com.chandler.learning.agent.learning.domain.LearningPlanUnitItem> unitItems, Long planId) {
        if (unitItems == null || unitItems.isEmpty()) {
            return List.of();
        }
        List<Long> unitIds = unitItems.stream().map(LearningPlanUnit::getId).toList();

        // 1. 场景关联词
        Map<Long, List<LearningSceneRelatedWord>> relatedWordsByUnit = relatedWordMapper.selectList(
                        new LambdaQueryWrapper<LearningSceneRelatedWord>()
                                .in(LearningSceneRelatedWord::getUnitId, unitIds)
                                .eq(LearningSceneRelatedWord::getDeleted, false)
                                .orderByAsc(LearningSceneRelatedWord::getUnitId)
                                .orderByAsc(LearningSceneRelatedWord::getSortOrder))
                .stream().collect(Collectors.groupingBy(LearningSceneRelatedWord::getUnitId));

        // 2. 联表查询词条 + 词汇进度
        List<com.chandler.learning.agent.learning.domain.LearningPlanUnitEntryItem> entries =
                unitEntryMapper.selectEntriesWithProgress(planId, unitIds);
        Map<Long, List<com.chandler.learning.agent.learning.domain.LearningPlanUnitEntryItem>> entriesByUnit = entries.stream()
                .collect(Collectors.groupingBy(LearningPlanUnitEntry::getUnitId));

        // 3. 评测通过记录
        Map<Long, List<String>> passedByEntry = reviewRecordMapper.selectList(
                        new LambdaQueryWrapper<LearningReviewRecord>()
                                .eq(LearningReviewRecord::getPlanId, planId)
                                .in(LearningReviewRecord::getUnitId, unitIds)
                                .eq(LearningReviewRecord::getCheckResult, LearningConstants.ScenePlan.CHECK_CORRECT)
                                .eq(LearningReviewRecord::getDeleted, false))
                .stream()
                .filter(record -> record.getEntryId() != null && record.getAssessmentType() != null)
                .collect(Collectors.groupingBy(LearningReviewRecord::getEntryId,
                        Collectors.mapping(LearningReviewRecord::getAssessmentType,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(LinkedHashSet::new), List::copyOf))));

        return unitItems.stream()
                .map(unitItem -> toUnitResponse(unitItem,
                        relatedWordsByUnit.getOrDefault(unitItem.getId(), List.of()),
                        entriesByUnit.getOrDefault(unitItem.getId(), List.of()),
                        passedByEntry))
                .toList();
    }

    private LearningPlanUnitResponse toUnitResponse(
            com.chandler.learning.agent.learning.domain.LearningPlanUnitItem unit,
            List<LearningSceneRelatedWord> relatedWords,
            List<com.chandler.learning.agent.learning.domain.LearningPlanUnitEntryItem> entries,
            Map<Long, List<String>> passedByEntry) {
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
        response.setSceneMaterialId(unit.getSceneMaterialId());
        response.setMaterialAvailable(unit.getSceneMaterialId() != null && StringUtils.hasText(unit.getMaterialLearningText()));
        response.setPendingChallengeWords(List.of());
        response.setLearningText(unit.getMaterialLearningText());
        response.setTranslation(unit.getMaterialTranslation());
        response.setMaterial(StringUtils.hasText(unit.getMaterialParsedJson()) ? unit.getMaterialParsedJson() : null);
        response.setMaterialRevision(unit.getMaterialRevisionNo());
        List<LearningSceneRelatedWord> matchedRelatedWords = relatedWords.stream()
                .filter(word -> unit.getMaterialId() != null
                        && Objects.equals(word.getSceneMaterialId(), unit.getMaterialId()))
                .toList();
        response.setRelatedWords(matchedRelatedWords.stream().map(this::toRelatedWordResponse).toList());
        response.setWords(entries.stream()
                .map(entry -> toEntryResponse(entry, passedByEntry))
                .toList());
        response.setGeneratedTime(unit.getGeneratedTime());
        response.setCompletedTime(unit.getCompletedTime());
        return response;
    }

    private SceneRelatedWordResponse toRelatedWordResponse(LearningSceneRelatedWord word) {
        SceneRelatedWordResponse response = new SceneRelatedWordResponse();
        response.setId(word.getId());
        response.setSceneMaterialId(word.getSceneMaterialId());
        response.setTerm(word.getTerm());
        response.setNormalizedTerm(word.getNormalizedTerm());
        response.setPhonetic(word.getPhonetic());
        response.setMeaning(word.getMeaningText());
        response.setContextMeaning(word.getContextMeaning());
        response.setCategoryCode(word.getCategoryCode());
        response.setCategoryName(word.getCategoryName());
        response.setPromoted(word.getPromoted());
        response.setPromotedEntryId(word.getPromotedEntryId());
        return response;
    }

    private LearningPlanUnitEntryResponse toEntryResponse(LearningPlanUnitEntryItem entry,
                                                          Map<Long, List<String>> passedByEntry) {
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
        response.setAcceptedSpellings(StringUtils.hasText(entry.getAcceptedSpellingsJson()) ? entry.getAcceptedSpellingsJson() : "[]");
        response.setAssessment(StringUtils.hasText(entry.getAssessmentJson()) ? entry.getAssessmentJson() : null);
        response.setPassedAssessments(entry.getWordbookEntryId() == null
                ? List.of()
                : passedByEntry.getOrDefault(entry.getWordbookEntryId(), List.of()));
        response.setFirstLearning(entry.getFirstLearning());
        if (entry.getWordProgressId() != null) {
            response.setLearningState(entry.getProgressLearningState());
            response.setRecognitionScore(entry.getProgressRecognitionScore());
            response.setSpellingScore(entry.getProgressSpellingScore());
            response.setCardStatus(entry.getProgressCardStatus());
        }
        return response;
    }

    private LearningPlanUnitEntryResponse toEntryResponse(LearningPlanUnitEntry entry,
                                                          LearningWordProgress progress,
                                                          Map<Long, List<String>> passedByEntry) {
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
        response.setAcceptedSpellings(StringUtils.hasText(entry.getAcceptedSpellingsJson()) ? entry.getAcceptedSpellingsJson() : "[]");
        response.setAssessment(StringUtils.hasText(entry.getAssessmentJson()) ? entry.getAssessmentJson() : null);
        response.setPassedAssessments(entry.getWordbookEntryId() == null
                ? List.of()
                : passedByEntry.getOrDefault(entry.getWordbookEntryId(), List.of()));
        response.setFirstLearning(entry.getFirstLearning());
        if (progress != null) {
            response.setLearningState(progress.getLearningState());
            response.setRecognitionScore(progress.getRecognitionScore());
            response.setSpellingScore(progress.getSpellingScore());
            response.setCardStatus(progress.getCardStatus());
        }
        return response;
    }
}
