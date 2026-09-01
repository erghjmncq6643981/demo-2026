package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogAnalysisService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanVocabularySelectorTest {

    @Mock
    private LearningPlanUnitEntryMapper unitEntryMapper;
    @Mock
    private VocabularyCatalogQueryService catalogQueryService;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private VocabularyCatalogAnalysisService catalogAnalysisService;
    @Mock
    private AiTaskExecutionService executionService;

    @Test
    @DisplayName("排除已编排进单元的词与已掌握词")
    void excludesAlreadyArrangedAndMasteredWords() {
        LearningPlan plan = new LearningPlan();
        plan.setId(10L);
        plan.setUserId(20L);
        plan.setCatalogVersionId(30L);

        LearningPlanUnitEntry arranged = new LearningPlanUnitEntry();
        arranged.setCatalogEntryId(1L);
        when(unitEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(arranged));

        VocabularyCatalogEntry first = entry(1L, "first");
        VocabularyCatalogEntry mastered = entry(2L, "mastered");
        VocabularyCatalogEntry eligible = entry(3L, "eligible");
        when(catalogQueryService.listPublishedEntries(30L)).thenReturn(List.of(first, mastered, eligible));
        when(catalogAnalysisService.readyEntries(30L)).thenReturn(List.of());

        LearningWordProgress masteredProgress = new LearningWordProgress();
        masteredProgress.setNormalizedTerm("mastered");
        masteredProgress.setLearningState("mastered");
        when(progressService.findByTerms(any(), any())).thenReturn(List.of(masteredProgress));

        LearningPlanVocabularySelector selector = new LearningPlanVocabularySelector(
                unitEntryMapper, catalogQueryService, progressService, catalogAnalysisService, executionService);

        assertThat(selector.nextCandidates(plan, 8)).extracting(VocabularyCatalogEntry::getId)
                .containsExactly(3L);
    }

    @Test
    @DisplayName("排除其他活动任务在步骤 1 中已锁定的词")
    void excludesWordsLockedByOtherActiveTasks() {
        LearningPlan plan = new LearningPlan();
        plan.setId(10L);
        plan.setUserId(20L);
        plan.setCatalogVersionId(30L);

        when(unitEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(executionService.findLockedCatalogEntryIds(10L, 999L)).thenReturn(Set.of(3L));

        VocabularyCatalogEntry first = entry(1L, "apple");
        VocabularyCatalogEntry lockedByOther = entry(3L, "banana");
        VocabularyCatalogEntry eligible = entry(4L, "orange");
        when(catalogQueryService.listPublishedEntries(30L)).thenReturn(List.of(first, lockedByOther, eligible));
        when(catalogAnalysisService.readyEntries(30L)).thenReturn(List.of());
        when(progressService.findByTerms(any(), any())).thenReturn(List.of());

        LearningPlanVocabularySelector selector = new LearningPlanVocabularySelector(
                unitEntryMapper, catalogQueryService, progressService, catalogAnalysisService, executionService);

        List<VocabularyCatalogEntry> candidates = selector.nextCandidates(plan, 8, List.of(), 999L);
        assertThat(candidates).extracting(VocabularyCatalogEntry::getId)
                .containsExactly(1L, 4L);
    }

    private VocabularyCatalogEntry entry(Long id, String term) {
        VocabularyCatalogEntry entry = new VocabularyCatalogEntry();
        entry.setId(id);
        entry.setNormalizedTerm(term);
        entry.setOriginalTerm(term);
        return entry;
    }
}
