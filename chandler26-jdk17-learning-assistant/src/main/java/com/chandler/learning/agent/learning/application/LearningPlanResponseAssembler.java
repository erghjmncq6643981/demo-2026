package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.learning.api.LearningPlanResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitEntryResponse;
import com.chandler.learning.agent.learning.api.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.domain.LearningPlan;
import com.chandler.learning.agent.learning.domain.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.LearningReviewRecord;
import com.chandler.learning.agent.learning.domain.LearningSceneMaterial;
import com.chandler.learning.agent.vocabulary.domain.LearningWordProgress;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningReviewRecordMapper;
import com.chandler.learning.agent.learning.infrastructure.LearningSceneMaterialMapper;
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
 * 计划详情按计划一次性加载单元、材料、词条、进度和检测记录，避免逐单元 N+1 查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPlanResponseAssembler {

    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final LearningWordProgressService progressService;
    private final LearningReviewRecordMapper reviewRecordMapper;
    private final ObjectMapper objectMapper;

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
        return loadUnitResponses(units, planId);
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
        List<LearningPlanUnit> units = unitMapper.selectList(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .eq(LearningPlanUnit::getDeleted, false)
                .orderByAsc(LearningPlanUnit::getUnitNo));
        return loadUnitResponses(units, planId);
    }

    private List<LearningPlanUnitResponse> loadUnitResponses(List<LearningPlanUnit> units, Long planId) {
        if (units.isEmpty()) {
            return List.of();
        }
        List<Long> unitIds = units.stream().map(LearningPlanUnit::getId).toList();
        Map<Long, LearningSceneMaterial> materialByUnit = materialMapper.selectList(
                        new LambdaQueryWrapper<LearningSceneMaterial>()
                                .in(LearningSceneMaterial::getUnitId, unitIds)
                                .eq(LearningSceneMaterial::getDeleted, false))
                .stream()
                .collect(Collectors.toMap(LearningSceneMaterial::getUnitId, Function.identity(), (left, right) -> left));
        List<LearningPlanUnitEntry> entries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getPlanId, planId)
                        .in(LearningPlanUnitEntry::getUnitId, unitIds)
                        .eq(LearningPlanUnitEntry::getDeleted, false)
                        .orderByAsc(LearningPlanUnitEntry::getUnitId)
                        .orderByAsc(LearningPlanUnitEntry::getSortOrder));
        Map<Long, List<LearningPlanUnitEntry>> entriesByUnit = entries.stream()
                .collect(Collectors.groupingBy(LearningPlanUnitEntry::getUnitId));
        Set<Long> progressIds = entries.stream()
                .map(LearningPlanUnitEntry::getWordProgressId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LearningWordProgress> progressById = progressIds.isEmpty()
                ? Map.of()
                : progressService.findByIds(progressIds).stream()
                        .collect(Collectors.toMap(LearningWordProgress::getId, Function.identity()));
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
        return units.stream()
                .map(unit -> toUnitResponse(unit, materialByUnit.get(unit.getId()),
                        entriesByUnit.getOrDefault(unit.getId(), List.of()), progressById, passedByEntry))
                .toList();
    }

    private LearningPlanUnitResponse toUnitResponse(LearningPlanUnit unit, LearningSceneMaterial material,
                                                    List<LearningPlanUnitEntry> entries,
                                                    Map<Long, LearningWordProgress> progressById,
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
        response.setLearningText(material == null ? null : material.getLearningText());
        response.setTranslation(material == null ? null : material.getTranslation());
        response.setMaterial(material == null ? null : readJson(material.getParsedJson()));
        response.setWords(entries.stream()
                .map(entry -> toEntryResponse(entry, progressById.get(entry.getWordProgressId()), passedByEntry))
                .toList());
        response.setGeneratedTime(unit.getGeneratedTime());
        response.setCompletedTime(unit.getCompletedTime());
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
        response.setAcceptedSpellings(readStringList(entry.getAcceptedSpellingsJson()));
        response.setAssessment(readJson(entry.getAssessmentJson()));
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

    private Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            log.debug("学习计划响应 JSON 读取失败 error={}", ex.getMessage());
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.debug("学习计划可接受拼写 JSON 读取失败 error={}", ex.getMessage());
            return List.of();
        }
    }
}
