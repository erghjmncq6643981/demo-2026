package com.chandler.learning.agent.task.application;

import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskMapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
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

    @Mock
    private AiTaskExecutionService executionService;

    private AiAsyncTaskService service;

    @BeforeEach
    void setUp() {
        service = new AiAsyncTaskService(taskMapper, new ObjectMapper(), systemLogService,
                userDisplayNameService, executionService);
    }

    @Test
    void defaultsToImmediateExecution() {
        AiAsyncTask task = service.create(1L, AiTaskConstants.TYPE_SCENE_MATERIAL,
                "生成场景材料", 2L, null, null, null, null, null, 1,
                Map.of("recommendedDate", "2026-08-18"));

        assertThat(task.getExecutionMode()).isEqualTo(AiTaskConstants.EXECUTION_IMMEDIATE);
        assertThat(task.getScheduledTime()).isNotNull();
        assertThat(task.getPayloadJson()).contains("2026-08-18");
        assertThat(task.getOwnerUserId()).isEqualTo(1L);
        assertThat(task.getTriggerUserId()).isEqualTo(1L);
    }

    @Test
    void rejectsUnknownExecutionMode() {
        assertThatThrownBy(() -> service.create(1L, AiTaskConstants.TYPE_SCENE_MATERIAL,
                "生成场景材料", 2L, null, null, "unknown", null, null, 1, Map.of()))
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo(LearningErrorCode.AI_ASYNC_TASK_EXECUTION_MODE_INVALID.getCode());
        verifyNoInteractions(taskMapper);
    }
}
