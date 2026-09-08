package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.api.request.SceneMaterialNoteRequest;
import com.chandler.learning.agent.learning.api.response.SceneMaterialNoteResponse;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterial;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterialNote;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialNoteMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningSceneMaterialNoteServiceTest {

    @Mock
    private LearningPlanMapper planMapper;
    @Mock
    private LearningPlanUnitMapper unitMapper;
    @Mock
    private LearningSceneMaterialMapper materialMapper;
    @Mock
    private LearningSceneMaterialNoteMapper noteMapper;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;

    @InjectMocks
    private LearningSceneMaterialNoteService noteService;

    private final Long userId = 1001L;
    private final Long planId = 2001L;
    private final Long unitId = 3001L;
    private final Long materialId = 4001L;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, LearningPlan.class);
        TableInfoHelper.initTableInfo(assistant, LearningPlanUnit.class);
        TableInfoHelper.initTableInfo(assistant, LearningSceneMaterial.class);
        TableInfoHelper.initTableInfo(assistant, LearningSceneMaterialNote.class);
    }

    private void mockValidMaterial() {
        LearningPlan plan = new LearningPlan();
        plan.setId(planId);
        when(planMapper.selectOne(any())).thenReturn(plan);

        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setId(unitId);
        unit.setSceneMaterialId(materialId);
        when(unitMapper.selectOne(any())).thenReturn(unit);

        LearningSceneMaterial material = new LearningSceneMaterial();
        material.setId(materialId);
        material.setTitle("Scene 1 Material");
        when(materialMapper.selectOne(any())).thenReturn(material);
    }

    @Test
    void getReturnsEmptyContentWhenNoNoteExists() {
        mockValidMaterial();
        when(noteMapper.selectOne(any())).thenReturn(null);

        SceneMaterialNoteResponse response = noteService.get(userId, planId, unitId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNull();
        assertThat(response.getPlanId()).isEqualTo(planId);
        assertThat(response.getUnitId()).isEqualTo(unitId);
        assertThat(response.getSceneMaterialId()).isEqualTo(materialId);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContentFormat()).isEqualTo("markdown");
    }

    @Test
    void getReturnsExistingNoteContent() {
        mockValidMaterial();
        LearningSceneMaterialNote note = new LearningSceneMaterialNote();
        note.setId(5001L);
        note.setContent("# My Scene Note\nKey takeaways.");
        note.setContentFormat("markdown");
        note.setUpdateTime(LocalDateTime.now());
        when(noteMapper.selectOne(any())).thenReturn(note);

        SceneMaterialNoteResponse response = noteService.get(userId, planId, unitId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(5001L);
        assertThat(response.getContent()).contains("Key takeaways");
    }

    @Test
    void saveCreatesNewNoteWhenNoneExists() {
        mockValidMaterial();
        when(noteMapper.selectOne(any())).thenReturn(null);
        when(userDisplayNameService.userName(userId)).thenReturn("Learner");

        SceneMaterialNoteRequest request = new SceneMaterialNoteRequest();
        request.setContent("First note draft");

        SceneMaterialNoteResponse response = noteService.save(userId, planId, unitId, request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("First note draft");

        ArgumentCaptor<LearningSceneMaterialNote> captor = ArgumentCaptor.forClass(LearningSceneMaterialNote.class);
        verify(noteMapper).insert(captor.capture());
        LearningSceneMaterialNote inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(userId);
        assertThat(inserted.getPlanId()).isEqualTo(planId);
        assertThat(inserted.getUnitId()).isEqualTo(unitId);
        assertThat(inserted.getSceneMaterialId()).isEqualTo(materialId);
        assertThat(inserted.getContent()).isEqualTo("First note draft");

        verify(systemLogService).record(eq(userId), eq(SystemLogType.LEARNING_PLAN),
                eq("保存场景材料笔记"), eq("场景「Scene 1 Material」"));
    }

    @Test
    void saveUpdatesExistingNote() {
        mockValidMaterial();
        LearningSceneMaterialNote existing = new LearningSceneMaterialNote();
        existing.setId(5002L);
        existing.setContent("Old draft");
        when(noteMapper.selectOne(any())).thenReturn(existing);
        when(userDisplayNameService.userName(userId)).thenReturn("Learner");

        SceneMaterialNoteRequest request = new SceneMaterialNoteRequest();
        request.setContent("Updated draft");

        SceneMaterialNoteResponse response = noteService.save(userId, planId, unitId, request);

        assertThat(response.getContent()).isEqualTo("Updated draft");
        verify(noteMapper).updateById(existing);
        assertThat(existing.getContent()).isEqualTo("Updated draft");
    }

    @Test
    void throwsNotFoundWhenPlanNotFound() {
        when(planMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> noteService.get(userId, planId, unitId))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining(LearningErrorCode.LEARNING_PLAN_NOT_FOUND.getDefaultMessage());
    }

    @Test
    void throwsNotFoundWhenUnitHasNoSceneMaterial() {
        LearningPlan plan = new LearningPlan();
        plan.setId(planId);
        when(planMapper.selectOne(any())).thenReturn(plan);

        LearningPlanUnit unit = new LearningPlanUnit();
        unit.setId(unitId);
        unit.setSceneMaterialId(null);
        when(unitMapper.selectOne(any())).thenReturn(unit);

        assertThatThrownBy(() -> noteService.get(userId, planId, unitId))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining(LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND.getDefaultMessage());
    }
}
