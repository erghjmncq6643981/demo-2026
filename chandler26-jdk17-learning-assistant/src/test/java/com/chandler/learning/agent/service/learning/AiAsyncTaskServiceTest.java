package com.chandler.learning.agent.service.learning;

import com.chandler.learning.agent.domain.entity.learning.AiAsyncTask;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.AiAsyncTaskMapper;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiAsyncTaskServiceTest {

    @Mock
    private AiAsyncTaskMapper taskMapper;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private UserDisplayNameService userDisplayNameService;

    private AiAsyncTaskService service;

    @BeforeEach
    void setUp() {
        service = new AiAsyncTaskService(taskMapper, new ObjectMapper(), systemLogService, userDisplayNameService);
    }

    @Test
    void defaultsToImmediateExecution() {
        AiAsyncTask task = service.create(1L, LearningConstants.AiTask.TYPE_SCENE_MATERIAL,
                "生成场景材料", 2L, null, null, null, null, null, 1,
                Map.of("recommendedDate", "2026-08-18"));

        assertThat(task.getExecutionMode()).isEqualTo(LearningConstants.AiTask.EXECUTION_IMMEDIATE);
        assertThat(task.getScheduledTime()).isNotNull();
        assertThat(task.getPayloadJson()).contains("2026-08-18");
    }

    @Test
    void rejectsUnknownExecutionMode() {
        assertThatThrownBy(() -> service.create(1L, LearningConstants.AiTask.TYPE_SCENE_MATERIAL,
                "生成场景材料", 2L, null, null, "unknown", null, null, 1, Map.of()))
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo(LearningConstants.ErrorCode.AI_ASYNC_TASK_EXECUTION_MODE_INVALID.getCode());
        verifyNoInteractions(taskMapper);
    }
}
