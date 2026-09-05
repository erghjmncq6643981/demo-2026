package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.learning.domain.bo.LearningAssessmentPassBO;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习计划进度查询与场景完成度计算。
 * <p>
 * 将计划编排服务中的查询策略集中管理，并把评测记录改为一次批量读取，
 * 避免在核心词循环中执行 SQL。
 */
@Service
@RequiredArgsConstructor
public class LearningPlanProgressQueryService {

    private final LearningPlanUnitMapper unitMapper;
    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final LearningReviewRecordMapper reviewRecordMapper;

    /** 根据计划剩余天数计算本次建议生成的核心词数量。 */
    public int targetWordCount(LearningPlan plan) {
        int target = ScenePlanConstants.MIN_CORE_WORDS;
        if (plan.getEndTime() != null) {
            LocalDate today = LocalDate.now();
            LocalDate planStart = plan.getStartTime() != null ? plan.getStartTime().toLocalDate() : today;
            LocalDate planEnd = plan.getEndTime().toLocalDate();
            LocalDate startForRemaining = today.isAfter(planStart) ? today : planStart;
            long remainingDays = ChronoUnit.DAYS.between(startForRemaining, planEnd) + 1;
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

    /** 查找当前场景之后最早的未完成场景，找不到时回退到全局最早未完成场景。 */
    public LearningPlanUnit findNextIncompleteUnit(Long planId, LearningPlanUnit currentUnit) {
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
                                    .gt(currentUnitNo != null, LearningPlanUnit::getUnitNo, currentUnitNo)))
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

    /** 判断计划中是否还存在未完成场景。 */
    public boolean hasIncompleteUnit(Long planId) {
        return unitMapper.selectCount(new LambdaQueryWrapper<LearningPlanUnit>()
                .eq(LearningPlanUnit::getPlanId, planId)
                .ne(LearningPlanUnit::getStatus, ScenePlanConstants.UNIT_COMPLETED)
                .eq(LearningPlanUnit::getDeleted, false)) > CommonConstants.ZERO;
    }

    /**
     * 批量刷新场景已完成核心词数量。
     * <p>核心词列表和评测记录各查询一次，禁止退化为逐词 SQL。</p>
     */
    public int refreshCompletedCoreCount(LearningPlanUnit unit) {
        List<LearningPlanUnitEntry> coreEntries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getUnitId, unit.getId())
                        .eq(LearningPlanUnitEntry::getTier, ScenePlanConstants.TIER_CORE)
                        .eq(LearningPlanUnitEntry::getDeleted, false));
        if (coreEntries.isEmpty()) {
            unit.setCompletedCoreCount(CommonConstants.ZERO);
            return CommonConstants.ZERO;
        }
        List<Long> entryIds = coreEntries.stream()
                .map(LearningPlanUnitEntry::getWordbookEntryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Set<String>> passedByEntry = entryIds.isEmpty()
                ? Map.of()
                : reviewRecordMapper.selectPassedAssessmentTypesBatch(unit.getId(), entryIds).stream()
                        .collect(Collectors.groupingBy(LearningAssessmentPassBO::getEntryId,
                                Collectors.mapping(LearningAssessmentPassBO::getAssessmentType,
                                        Collectors.toCollection(HashSet::new))));
        int completed = (int) coreEntries.stream()
                .filter(entry -> entry.getWordbookEntryId() != null)
                .filter(entry -> isEntryComplete(entry.getMasteryRequirement(),
                        passedByEntry.getOrDefault(entry.getWordbookEntryId(), Set.of())))
                .count();
        unit.setCompletedCoreCount(completed);
        unit.setUpdateTime(java.time.LocalDateTime.now());
        unitMapper.updateById(unit);
        return completed;
    }

    /** 判断当前词条是否已经完成计划要求的所有评测。 */
    public boolean isEntryComplete(String masteryRequirement, Set<String> passed) {
        boolean meaningPassed = passed.contains(ScenePlanConstants.ASSESSMENT_MEANING_CHOICE);
        boolean spellingPassed = !ScenePlanConstants.MASTERY_SPELLING.equals(masteryRequirement)
                || (passed.contains(ScenePlanConstants.ASSESSMENT_COPY_TYPING)
                && passed.contains(ScenePlanConstants.ASSESSMENT_MEANING_SPELLING));
        return meaningPassed && spellingPassed;
    }

    private int value(Integer value) {
        return value == null ? CommonConstants.ZERO : value;
    }
}
