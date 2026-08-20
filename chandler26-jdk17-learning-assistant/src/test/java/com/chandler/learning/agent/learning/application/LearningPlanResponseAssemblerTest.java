package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.learning.api.LearningPlanResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
    private LearningWordProgressService progressService;
    @Mock
    private LearningReviewRecordMapper reviewRecordMapper;

    @Test
    void loadsPlanRelationsInBatchesInsteadOfPerUnitQueries() {
        LearningPlan plan = new LearningPlan();
        plan.setId(10L);
        plan.setStatus("active");

        LearningPlanUnit firstUnit = unit(101L, 10L, 1);
        LearningPlanUnit secondUnit = unit(102L, 10L, 2);
        when(unitMapper.selectList(any(Wrapper.class))).thenReturn(List.of(firstUnit, secondUnit));

        LearningSceneMaterial material = new LearningSceneMaterial();
        material.setUnitId(101L);
        material.setLearningText("Scene text");
        material.setParsedJson("{}");
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(material));

        LearningPlanUnitEntry firstEntry = entry(1001L, 101L, 2001L, 3001L, "airport");
        LearningPlanUnitEntry secondEntry = entry(1002L, 102L, 2002L, 3002L, "ticket");
        when(unitEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(firstEntry, secondEntry));

        LearningWordProgress firstProgress = progress(3001L, "learning");
        LearningWordProgress secondProgress = progress(3002L, "learned");
        when(progressService.findByIds(any())).thenReturn(List.of(firstProgress, secondProgress));

        LearningReviewRecord record = new LearningReviewRecord();
        record.setEntryId(2002L);
        record.setAssessmentType("meaning_choice");
        when(reviewRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(record));

        LearningPlanResponseAssembler assembler = new LearningPlanResponseAssembler(
                unitMapper, unitEntryMapper, materialMapper, progressService, reviewRecordMapper, new ObjectMapper());

        LearningPlanResponse response = assembler.toPlanResponse(plan, true);

        assertThat(response.getUnits()).hasSize(2);
        assertThat(response.getUnits().get(0).getLearningText()).isEqualTo("Scene text");
        assertThat(response.getUnits().get(1).getWords().get(0).getPassedAssessments())
                .containsExactly("meaning_choice");
        verify(materialMapper).selectList(any(Wrapper.class));
        verify(unitEntryMapper).selectList(any(Wrapper.class));
        verify(progressService).findByIds(any());
        verify(reviewRecordMapper).selectList(any(Wrapper.class));
    }

    private LearningPlanUnit unit(Long id, Long planId, int unitNo) {
        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setId(id);
        unit.setPlanId(planId);
        unit.setUnitNo(unitNo);
        unit.setStatus("ready");
        return unit;
    }

    private LearningPlanUnitEntry entry(Long id, Long unitId, Long wordbookEntryId,
                                        Long progressId, String term) {
        LearningPlanUnitEntry entry = new LearningPlanUnitEntry();
        entry.setId(id);
        entry.setPlanId(10L);
        entry.setUnitId(unitId);
        entry.setWordbookEntryId(wordbookEntryId);
        entry.setWordProgressId(progressId);
        entry.setTerm(term);
        entry.setTier("core");
        entry.setAcceptedSpellingsJson("[]");
        entry.setAssessmentJson("{}");
        return entry;
    }

    private LearningWordProgress progress(Long id, String state) {
        LearningWordProgress progress = new LearningWordProgress();
        progress.setId(id);
        progress.setLearningState(state);
        return progress;
    }
}
