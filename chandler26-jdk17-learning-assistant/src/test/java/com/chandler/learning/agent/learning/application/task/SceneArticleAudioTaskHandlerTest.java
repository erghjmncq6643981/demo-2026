package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneArticleAudioTaskHandlerTest {

    @Test
    @DisplayName("场景文章语音生成任务正常执行并更新进度与状态")
    void executesAudioGenerationSuccessfully() {
        SceneArticleAudioService audioService = mock(SceneArticleAudioService.class);
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        AiAsyncTaskService taskService = mock(AiAsyncTaskService.class);

        SceneArticleAudioTaskHandler handler = new SceneArticleAudioTaskHandler(
                audioService, executionService, taskService);

        assertThat(handler.taskType()).isEqualTo(AiTaskType.SCENE_ARTICLE_AUDIO);
        assertThat(handler.steps()).hasSize(1);
        assertThat(handler.steps().get(0).code()).isEqualTo("synthesize_audio");

        AiAsyncTask task = new AiAsyncTask();
        task.setId(9001L);
        task.setOwnerUserId(1001L);
        task.setOperatorUserId(1001L);
        task.setUnitId(5001L);

        Map<String, Object> payload = Map.of("forceRefresh", "true");

        when(executionService.execute(eq(9001L), eq("synthesize_audio"), eq(1001L), any(), any()))
                .thenAnswer((InvocationOnMock invocation) -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });

        handler.execute(task, payload);

        verify(audioService).generateOrGetSceneAudio(5001L, true);
        verify(taskService).updateProgress(9001L, 1, 1, 0);
        verify(taskService).complete(9001L, AiTaskConstants.STATUS_COMPLETED, null);
    }
}
