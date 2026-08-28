package com.chandler.learning.agent.task.application;

import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTaskStep;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskAttemptMapper;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskStepMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AiTaskExecutionServiceTest {

    @Test
    void initializesStableStepsWithOneBatchInsert() {
        AiAsyncTaskStepMapper stepMapper = mock(AiAsyncTaskStepMapper.class);
        AiTaskExecutionService service = new AiTaskExecutionService(stepMapper, mock(AiAsyncTaskAttemptMapper.class),
                Executors.newSingleThreadScheduledExecutor());

        service.initialize(100L, 200L, List.of(
                new AiTaskStepDefinition("generate", "生成材料", 1, 3),
                new AiTaskStepDefinition("enrich", "补充相关词", 2, 1)));

        ArgumentCaptor<List<AiAsyncTaskStep>> captor = ArgumentCaptor.forClass(List.class);
        verify(stepMapper).insertBatch(captor.capture());
        verify(stepMapper, never()).insert(org.mockito.ArgumentMatchers.any(AiAsyncTaskStep.class));
        assertThat(captor.getValue())
                .extracting(AiAsyncTaskStep::getStepCode)
                .containsExactly("generate", "enrich");
    }
}
