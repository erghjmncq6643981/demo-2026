package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.entity.learning.LearningPlan;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import com.chandler.learning.agent.domain.entity.learning.LearningWordProgress;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntryAnalysis;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordProgressMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.service.vocabulary.VocabularyCatalogAnalysisService;
import com.chandler.learning.agent.support.LearningConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习计划选词策略。
 * <p>
 * 优先使用公共词本预分析得到的语义分组，把相关词放进同一批候选；同时排除已编排词和已掌握词。
 */
@Component
@RequiredArgsConstructor
public class LearningPlanVocabularySelector {

    private final LearningPlanUnitEntryMapper unitEntryMapper;
    private final VocabularyCatalogEntryMapper catalogEntryMapper;
    private final LearningWordProgressMapper progressMapper;
    private final LearningWordProgressService progressService;
    private final VocabularyCatalogAnalysisService catalogAnalysisService;

    /** 选择尚未被任何场景编排的新核心词候选。 */
    public List<VocabularyCatalogEntry> nextCandidates(LearningPlan plan, int requestedLimit) {
        return nextCandidates(plan, requestedLimit, List.of());
    }

    /** 根据待复习词的语义分组优先选择相关的新词。 */
    public List<VocabularyCatalogEntry> nextCandidates(LearningPlan plan, int requestedLimit,
                                                       List<VocabularyCatalogEntry> reviewWords) {
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
        int candidateLimit = Math.max(LearningConstants.SEQUENCE_STEP, requestedLimit);
        List<VocabularyCatalogEntryAnalysis> analyses = catalogAnalysisService.readyEntries(plan.getCatalogVersionId());
        if (!analyses.isEmpty()) {
            sortBySemanticRelevance(all, arranged, reviewWords, analyses);
        }
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
            if (result.size() >= candidateLimit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    /** 选择历史场景中尚未掌握的核心词，作为新材料的复习词而不改变原场景归属。 */
    public List<VocabularyCatalogEntry> pendingReviewWords(LearningPlan plan, int limit) {
        List<LearningPlanUnitEntry> previousEntries = unitEntryMapper.selectList(
                new LambdaQueryWrapper<LearningPlanUnitEntry>()
                        .eq(LearningPlanUnitEntry::getPlanId, plan.getId())
                        .eq(LearningPlanUnitEntry::getTier, LearningConstants.ScenePlan.TIER_CORE)
                        .isNotNull(LearningPlanUnitEntry::getCatalogEntryId)
                        .eq(LearningPlanUnitEntry::getDeleted, false)
                        .orderByDesc(LearningPlanUnitEntry::getUpdateTime));
        List<Long> entryIds = previousEntries.stream()
                .map(LearningPlanUnitEntry::getCatalogEntryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (entryIds.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTerms = previousEntries.stream()
                .map(LearningPlanUnitEntry::getNormalizedTerm)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, LearningWordProgress> progressByTerm = normalizedTerms.isEmpty()
                ? Map.of()
                : progressMapper.selectList(new LambdaQueryWrapper<LearningWordProgress>()
                                .eq(LearningWordProgress::getUserId, plan.getUserId())
                                .in(LearningWordProgress::getNormalizedTerm, normalizedTerms)
                                .eq(LearningWordProgress::getDeleted, false))
                        .stream()
                        .collect(Collectors.toMap(LearningWordProgress::getNormalizedTerm,
                                item -> item, (left, right) -> left));
        Map<Long, VocabularyCatalogEntry> catalogEntries = catalogEntryMapper.selectBatchIds(entryIds).stream()
                .collect(Collectors.toMap(VocabularyCatalogEntry::getId, item -> item));
        List<VocabularyCatalogEntry> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (LearningPlanUnitEntry previous : previousEntries) {
            LearningWordProgress progress = progressByTerm.get(previous.getNormalizedTerm());
            if (progress != null && LearningConstants.ScenePlan.PROGRESS_MASTERED.equals(progress.getLearningState())) {
                continue;
            }
            VocabularyCatalogEntry entry = catalogEntries.get(previous.getCatalogEntryId());
            if (entry != null && seen.add(entry.getId())) {
                result.add(entry);
            }
            if (result.size() >= Math.min(limit, LearningConstants.ScenePlan.MAX_REVIEW_WORDS)) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private void sortBySemanticRelevance(List<VocabularyCatalogEntry> entries, Set<Long> arranged,
                                         List<VocabularyCatalogEntry> reviewWords,
                                         List<VocabularyCatalogEntryAnalysis> analyses) {
        Map<Long, VocabularyCatalogEntryAnalysis> analysisByEntry = analyses.stream()
                .collect(Collectors.toMap(VocabularyCatalogEntryAnalysis::getCatalogEntryId,
                        item -> item, (left, right) -> left));
        Set<String> preferredGroups = reviewWords.stream()
                .map(item -> analysisByEntry.get(item.getId()))
                .filter(java.util.Objects::nonNull)
                .map(VocabularyCatalogEntryAnalysis::getPrimaryGroupCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> groupSize = entries.stream()
                .filter(item -> !arranged.contains(item.getId()))
                .map(item -> analysisByEntry.get(item.getId()))
                .filter(java.util.Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getPrimaryGroupCode()))
                .collect(Collectors.groupingBy(VocabularyCatalogEntryAnalysis::getPrimaryGroupCode,
                        Collectors.summingInt(item -> LearningConstants.SEQUENCE_STEP)));
        entries.sort((left, right) -> Integer.compare(
                candidateScore(right, analysisByEntry, preferredGroups, groupSize),
                candidateScore(left, analysisByEntry, preferredGroups, groupSize)));
    }

    private int candidateScore(VocabularyCatalogEntry entry,
                               Map<Long, VocabularyCatalogEntryAnalysis> analysisByEntry,
                               Set<String> preferredGroups, Map<String, Integer> groupSize) {
        VocabularyCatalogEntryAnalysis analysis = analysisByEntry.get(entry.getId());
        if (analysis == null) {
            return LearningConstants.ZERO;
        }
        int score = groupSize.getOrDefault(analysis.getPrimaryGroupCode(), LearningConstants.ZERO);
        if (preferredGroups.contains(analysis.getPrimaryGroupCode())) {
            score += LearningConstants.ScenePlan.PREFERRED_GROUP_SCORE;
        }
        if (StringUtils.hasText(analysis.getSubTopicCode())) {
            score += LearningConstants.ScenePlan.SUB_TOPIC_SCORE;
        }
        return score;
    }
}
