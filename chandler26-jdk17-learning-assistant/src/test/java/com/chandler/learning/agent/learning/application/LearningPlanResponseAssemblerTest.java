package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.learning.api.response.LearningPlanResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitItem;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitEntryItem;
import com.chandler.learning.agent.learning.domain.entity.LearningReviewRecord;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterial;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordProgress;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneRelatedWordMapper;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.domain.bo.LearningPlanUnitWordSummaryItem;
import com.chandler.learning.agent.learning.domain.bo.LearningAssessmentPassBO;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanResponseAssemblerTest {

    @Mock
    private LearningPlanUnitMapper unitMapper;
    @Mock
    private LearningPlanUnitEntryMapper unitEntryMapper;
    @Mock
    private LearningSceneMaterialMapper materialMapper;
    @Mock
    private LearningSceneRelatedWordMapper relatedWordMapper;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private LearningReviewRecordMapper reviewRecordMapper;

    @Test
    void loadsPlanRelationsInBatchesInsteadOfPerUnitQueries() {
        LearningPlan plan = new LearningPlan();
        plan.setId(10L);
        plan.setStatus("active");

        LearningPlanUnitItem firstUnit = unitItem(101L, 10L, 1, 1001L, "Scene text", "{}");
        LearningPlanUnitItem secondUnit = unitItem(102L, 10L, 2, null, null, null);
        when(unitMapper.selectUnitsWithMaterial(10L, null)).thenReturn(List.of(firstUnit, secondUnit));
        when(unitMapper.selectGeneratedCoreCount(10L)).thenReturn(40);
        when(relatedWordMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        LearningPlanUnitEntryItem firstEntry = entryItem(1001L, 101L, 2001L, 3001L, "airport", "learning");
        LearningPlanUnitEntryItem secondEntry = entryItem(1002L, 102L, 2002L, 3002L, "ticket", "learned");
        when(unitEntryMapper.selectEntriesWithProgress(any(), any())).thenReturn(List.of(firstEntry, secondEntry));

        LearningReviewRecord record = new LearningReviewRecord();
        record.setEntryId(2002L);
        record.setAssessmentType("meaning_choice");
        when(reviewRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record));

        LearningPlanResponseAssembler assembler = new LearningPlanResponseAssembler(
                unitMapper, unitEntryMapper, materialMapper, relatedWordMapper,
                progressService, reviewRecordMapper);

        LearningPlanResponse response = assembler.toPlanResponse(plan, true);

        assertThat(response.getGeneratedCoreWords()).isEqualTo(40);
        assertThat(response.getUnits()).hasSize(2);
        assertThat(response.getUnits().get(0).getLearningText()).isEqualTo("Scene text");
        assertThat(response.getUnits().get(1).getWords().get(0).getPassedAssessments())
                .containsExactly("meaning_choice");
        verify(unitMapper).selectUnitsWithMaterial(10L, null);
        verify(unitEntryMapper).selectEntriesWithProgress(any(), any());
        verify(reviewRecordMapper).selectList(any(Wrapper.class));
    }

    @Test
    void assemblesUnitSummaryResponsesWithWords() {
        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setId(101L);
        unit.setPlanId(10L);
        unit.setUnitNo(1);
        unit.setTitle("Scene 1");
        unit.setCoreWordCount(1);
        unit.setCompletedCoreCount(0);

        LearningPlanUnitWordSummaryItem wordItem = new LearningPlanUnitWordSummaryItem();
        wordItem.setId(1001L);
        wordItem.setUnitId(101L);
        wordItem.setWordbookEntryId(2001L);
        wordItem.setTerm("declutter");
        wordItem.setPhonetic("/diːˈklʌtər/");
        wordItem.setMeaning("清理");
        wordItem.setTier("core");
        wordItem.setMasteryRequirement("spelling");

        when(unitEntryMapper.selectWordSummaries(10L, List.of(101L))).thenReturn(List.of(wordItem));
        when(reviewRecordMapper.selectPassedAssessmentTypesByUnits(List.of(101L), List.of(2001L)))
                .thenReturn(List.of());

        LearningPlanResponseAssembler assembler = new LearningPlanResponseAssembler(
                unitMapper, unitEntryMapper, materialMapper, relatedWordMapper,
                progressService, reviewRecordMapper);

        List<LearningPlanUnitResponse> summaries = assembler.toUnitSummaryResponses(List.of(unit), 10L);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getPendingChallengeWords()).hasSize(1);
        assertThat(summaries.get(0).getPendingChallengeWords().get(0).getTerm()).isEqualTo("declutter");
        assertThat(summaries.get(0).getPendingChallengeWords().get(0).getCompleted()).isFalse();
    }

    private LearningPlanUnitItem unitItem(Long id, Long planId, int unitNo,
                                          Long materialId, String text, String parsedJson) {
        LearningPlanUnitItem unit = new LearningPlanUnitItem();
        unit.setId(id);
        unit.setPlanId(planId);
        unit.setUnitNo(unitNo);
        unit.setStatus("ready");
        unit.setMaterialId(materialId);
        unit.setMaterialLearningText(text);
        unit.setMaterialParsedJson(parsedJson);
        return unit;
    }

    private LearningPlanUnitEntryItem entryItem(Long id, Long unitId, Long wordbookEntryId,
                                                Long progressId, String term, String progressState) {
        LearningPlanUnitEntryItem entry = new LearningPlanUnitEntryItem();
        entry.setId(id);
        entry.setPlanId(10L);
        entry.setUnitId(unitId);
        entry.setWordbookEntryId(wordbookEntryId);
        entry.setWordProgressId(progressId);
        entry.setTerm(term);
        entry.setTier("core");
        entry.setAcceptedSpellingsJson("[]");
        entry.setAssessmentJson("{}");
        entry.setProgressLearningState(progressState);
        return entry;
    }

    private LearningWordProgress progress(Long id, String state) {
        LearningWordProgress progress = new LearningWordProgress();
        progress.setId(id);
        progress.setLearningState(state);
        return progress;
    }
}
