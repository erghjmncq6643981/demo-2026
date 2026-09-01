package com.chandler.learning.agent.learning.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.learning.domain.bo.PreparedUnitGroup;
import com.chandler.learning.agent.learning.domain.bo.PreparedVocabularyBatch;
import com.chandler.learning.agent.learning.domain.constant.ScenePlanConstants;
import com.chandler.learning.agent.learning.domain.entity.LearningPlan;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogQueryService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningPlanServicePipelineTest {

    @Mock
    private LearningPlanMapper planMapper;
    @Mock
    private LearningPlanUnitMapper unitMapper;
    @Mock
    private LearningPlanUnitEntryMapper unitEntryMapper;
    @Mock
    private LearningReviewRecordMapper reviewRecordMapper;
    @Mock
    private VocabularyCatalogQueryService catalogQueryService;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private WordbookService wordbookService;
    @Mock
    private ReviewSchedulePolicy reviewSchedulePolicy;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;
    @Mock
    private AiTaskExecutionService executionService;
    @Mock
    private LearningPlanVocabularySelector vocabularySelector;
    @Mock
    private LearningPlanSceneContentService sceneContentService;
    @Mock
    private LearningPlanScenePersistenceService scenePersistenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private LearningPlanService planService;

    @Test
    @DisplayName("步骤 1：加短事务锁选词并保存 Checkpoint 后立即释放锁")
    void prepareVocabularyForTaskLocksSavesCheckpointAndReleasesLock() {
        LearningPlan plan = new LearningPlan();
        plan.setId(1001L);
        plan.setUserId(2001L);
        plan.setStatus(ScenePlanConstants.STATUS_ACTIVE);
        plan.setTotalCatalogWords(500);
        plan.setLearnedCoreWords(0);
        plan.setStartTime(LocalDateTime.now().minusDays(1));
        plan.setEndTime(LocalDateTime.now().plusDays(30));

        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(plan);
        when(planMapper.claimGenerationLock(eq(1001L), any(), any(), any())).thenReturn(1);

        VocabularyCatalogEntry entry1 = new VocabularyCatalogEntry();
        entry1.setId(101L);
        VocabularyCatalogEntry entry2 = new VocabularyCatalogEntry();
        entry2.setId(102L);

        when(vocabularySelector.pendingReviewWords(eq(plan), anyInt())).thenReturn(List.of());
        when(vocabularySelector.nextCandidates(eq(plan), anyInt(), any(), eq(5001L)))
                .thenReturn(List.of(entry1, entry2));

        LocalDate date = LocalDate.of(2026, 9, 7);
        PreparedVocabularyBatch batch = planService.prepareVocabularyForTask(2001L, 1001L, date, 5001L);

        assertThat(batch).isNotNull();
        assertThat(batch.getPlanId()).isEqualTo(1001L);
        assertThat(batch.allCandidateEntryIds()).containsExactly(101L, 102L);

        verify(executionService).saveStepCheckpoint(eq(5001L), eq("prepare_vocabulary"), any(PreparedVocabularyBatch.class));
        verify(planMapper).releaseGenerationLock(eq(1001L), any());
    }

    @Test
    @DisplayName("步骤 2：重试时若 Checkpoint 中的词条已被占用，自动重新分配词组")
    void generateMaterialForTaskReAllocatesWhenCheckpointHasConflicts() {
        LearningPlan plan = new LearningPlan();
        plan.setId(1001L);
        plan.setUserId(2001L);
        plan.setStatus(ScenePlanConstants.STATUS_ACTIVE);
        plan.setTotalCatalogWords(500);
        plan.setLearnedCoreWords(0);
        plan.setStartTime(LocalDateTime.now().minusDays(1));
        plan.setEndTime(LocalDateTime.now().plusDays(30));

        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(plan);

        LocalDate date = LocalDate.of(2026, 9, 7);
        // 旧 checkpoint 包含了 101L 和 102L
        PreparedVocabularyBatch staleBatch = new PreparedVocabularyBatch(1001L, date, 2, List.of(
                new PreparedUnitGroup(0, List.of(101L, 102L), List.of(), 2)));

        when(executionService.getStepCheckpoint(5001L, "prepare_vocabulary", PreparedVocabularyBatch.class))
                .thenReturn(staleBatch);

        // 数据库中 101L 已经被另一个场景单元占用了
        LearningPlanUnitEntry occupied = new LearningPlanUnitEntry();
        occupied.setCatalogEntryId(101L);
        when(unitEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(occupied));

        // 重新分配选词
        when(planMapper.claimGenerationLock(eq(1001L), any(), any(), any())).thenReturn(1);
        VocabularyCatalogEntry freshEntry = new VocabularyCatalogEntry();
        freshEntry.setId(103L);
        when(vocabularySelector.pendingReviewWords(eq(plan), anyInt())).thenReturn(List.of());
        when(vocabularySelector.nextCandidates(eq(plan), anyInt(), any(), eq(5001L)))
                .thenReturn(List.of(freshEntry));

        when(catalogQueryService.findEntries(any())).thenReturn(List.of(freshEntry));

        com.chandler.learning.agent.ai.chat.application.AgentChatResponse aiResponse = org.mockito.Mockito.mock(com.chandler.learning.agent.ai.chat.application.AgentChatResponse.class);
        com.fasterxml.jackson.databind.JsonNode sceneNode = new ObjectMapper().createObjectNode();
        when(aiResponse.requireStructuredRoot(any())).thenReturn(sceneNode);
        when(sceneContentService.generateScene(any(), anyInt(), any(), any(), anyInt(), eq(88L)))
                .thenReturn(aiResponse);
        when(sceneContentService.validateSceneWords(any(), any(List.class), any(List.class), anyInt()))
                .thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenReturn(new com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse());

        planService.generateMaterialForTask(2001L, 1001L, 88L, date, 5001L);

        // 验证重新执行了选词并更新了 checkpoint
        verify(executionService).saveStepCheckpoint(eq(5001L), eq("prepare_vocabulary"), any(PreparedVocabularyBatch.class));
    }
}
