package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.domain.entity.learning.LearningPlan;
import com.chandler.learning.agent.domain.entity.learning.LearningPlanUnitEntry;
import com.chandler.learning.agent.domain.entity.learning.LearningWordProgress;
import com.chandler.learning.agent.domain.entity.vocabulary.VocabularyCatalogEntry;
import com.chandler.learning.agent.mapper.learning.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordProgressMapper;
import com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.service.vocabulary.VocabularyCatalogAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanVocabularySelectorTest {

    @Mock
    private LearningPlanUnitEntryMapper unitEntryMapper;
    @Mock
    private VocabularyCatalogEntryMapper catalogEntryMapper;
    @Mock
    private LearningWordProgressMapper progressMapper;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private VocabularyCatalogAnalysisService catalogAnalysisService;

    @Test
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
        when(catalogEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, mastered, eligible));
        when(catalogAnalysisService.readyEntries(30L)).thenReturn(List.of());

        LearningWordProgress masteredProgress = new LearningWordProgress();
        masteredProgress.setLearningState("mastered");
        when(progressService.find(20L, "mastered")).thenReturn(masteredProgress);
        when(progressService.find(20L, "eligible")).thenReturn(null);

        LearningPlanVocabularySelector selector = new LearningPlanVocabularySelector(
                unitEntryMapper, catalogEntryMapper, progressMapper, progressService, catalogAnalysisService);

        assertThat(selector.nextCandidates(plan, 8)).extracting(VocabularyCatalogEntry::getId)
                .containsExactly(3L);
    }

    private VocabularyCatalogEntry entry(Long id, String term) {
        VocabularyCatalogEntry entry = new VocabularyCatalogEntry();
        entry.setId(id);
        entry.setNormalizedTerm(term);
        entry.setOriginalTerm(term);
        return entry;
    }
}
