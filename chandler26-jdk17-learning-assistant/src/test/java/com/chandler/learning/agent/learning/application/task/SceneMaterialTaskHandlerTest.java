package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.api.response.LearningPlanUnitResponse;
import com.chandler.learning.agent.learning.application.LearningPlanService;
import com.chandler.learning.agent.learning.application.LearningSceneRelatedVocabularyService;
import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import com.chandler.learning.agent.learning.domain.bo.PreparedUnitGroup;
import com.chandler.learning.agent.learning.domain.bo.PreparedVocabularyBatch;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneMaterialTaskHandlerTest {

    @Test
    @DisplayName("批量生成场景材料任务正常按 4 步流水线执行（选词、生成材料、补充相关词、合成文章语音）")
    void executesFourStepPipelineSuccessfully() {
        LearningPlanService planService = mock(LearningPlanService.class);
        LearningSceneRelatedVocabularyService relatedVocabularyService = mock(LearningSceneRelatedVocabularyService.class);
        SceneArticleAudioService audioService = mock(SceneArticleAudioService.class);
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        AiAsyncTaskService taskService = mock(AiAsyncTaskService.class);

        SceneMaterialTaskHandler handler = new SceneMaterialTaskHandler(
                planService, relatedVocabularyService, audioService, executionService, taskService);

        assertThat(handler.steps()).hasSize(4);
        assertThat(handler.steps().get(0).code()).isEqualTo("prepare_vocabulary");
        assertThat(handler.steps().get(1).code()).isEqualTo("generate_material");
        assertThat(handler.steps().get(2).code()).isEqualTo("generate_related_words");
        assertThat(handler.steps().get(3).code()).isEqualTo("synthesize_audio");

        AiAsyncTask task = new AiAsyncTask();
        task.setId(1001L);
        task.setOwnerUserId(2001L);
        task.setOperatorUserId(2001L);
        task.setPlanId(3001L);

        LocalDate date = LocalDate.of(2026, 9, 6);
        Map<String, Object> payload = Map.of(
                "modelConfigId", 88L,
                "recommendedDate", date.toString()
        );

        PreparedVocabularyBatch batch = new PreparedVocabularyBatch(3001L, date, 36, List.of(
                new PreparedUnitGroup(0, List.of(101L, 102L), List.of(), 36)));

        LearningPlanUnitResponse generatedUnitResponse = new LearningPlanUnitResponse();
        generatedUnitResponse.setId(5001L);
        generatedUnitResponse.setRecommendedDate(date);

        LearningPlanUnit unitEntity = new LearningPlanUnit();
        unitEntity.setId(5001L);
        unitEntity.setPlanId(3001L);
        unitEntity.setRecommendedDate(date);

        when(planService.prepareVocabularyForTask(2001L, 3001L, date, 1001L))
                .thenReturn(batch);
        when(planService.generateMaterialForTask(2001L, 3001L, 88L, date, 1001L))
                .thenReturn(List.of(generatedUnitResponse));
        when(planService.findUnitsByIds(eq(3001L), eq(List.of(5001L))))
                .thenReturn(List.of(unitEntity));

        when(executionService.execute(anyLong(), any(), any(), any(), any()))
                .thenAnswer((InvocationOnMock invocation) -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier != null ? supplier.get() : null;
                });

        handler.execute(task, payload);

        verify(planService).prepareVocabularyForTask(2001L, 3001L, date, 1001L);
        verify(planService).generateMaterialForTask(2001L, 3001L, 88L, date, 1001L);
        verify(relatedVocabularyService).generate(
                eq(2001L),
                eq(3001L),
                eq(5001L),
                eq(88L),
                eq(LearningSceneRelatedVocabularyService.DEFAULT_TARGET_COUNT)
        );
        verify(audioService).generateOrGetSceneAudio(eq(5001L), eq(true));

        verify(taskService).complete(eq(1001L), eq(AiTaskConstants.STATUS_COMPLETED), any());
    }
}
