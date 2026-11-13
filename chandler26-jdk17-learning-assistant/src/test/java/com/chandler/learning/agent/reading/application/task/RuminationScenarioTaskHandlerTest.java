package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;
import com.chandler.learning.agent.reading.application.RuminationGraphPersistenceService;
import com.chandler.learning.agent.reading.application.RuminationSemanticEvaluator;
import com.chandler.learning.agent.reading.application.RuminationService;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataRetrievalFailureException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自定义场景两阶段异步任务处理器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationScenarioTaskHandlerTest {

    @Mock
    private RuminationService ruminationService;
    @Mock
    private LearningRuminationGraphMapper graphMapper;
    @Mock
    private RuminationSemanticEvaluator semanticEvaluator;
    @Mock
    private AiTaskExecutionService executionService;
    @Mock
    private AiAsyncTaskService taskService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuminationGraphPersistenceService graphPersistenceService;
    private RuminationScenarioTaskHandler handler;

    @BeforeEach
    void setUp() {
        graphPersistenceService = new RuminationGraphPersistenceService(
                graphMapper, semanticEvaluator, objectMapper);
        handler = new RuminationScenarioTaskHandler(
                ruminationService, graphPersistenceService, executionService, taskService);
    }

    @Test
    @DisplayName("阶段 2 落库失败重试：跳过阶段 1 模型构建，直接复用检查点幂等落库")
    void execute_stepTwoFailureThenRetry_skipsModelAndPersistsFromCheckpoint() {
        Long taskId = 8101L;
        Long userId = 1001L;
        Long wordbookId = 2001L;

        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "custom_clinic",
                "scenarioName", "社区诊所",
                "scenarioIcon", "🩺",
                "scenarioPrompt", "看病挂号问诊",
                "modelConfigId", 301L
        );

        RuminationNodeResponse hub = new RuminationNodeResponse();
        hub.setId("hub");
        hub.setNodeType("hub");
        hub.setTerm("社区诊所");
        RuminationNodeResponse core = new RuminationNodeResponse();
        core.setId("core-fever");
        core.setNodeType("core");
        core.setTerm("fever");
        RuminationEdgeResponse edge = new RuminationEdgeResponse();
        edge.setSource("hub");
        edge.setTarget("core-fever");

        RuminationScenarioCheckpoint generated = new RuminationScenarioCheckpoint(
                "custom_clinic", "社区诊所", "🩺", "ai", List.of(hub, core), List.of(edge));
        when(ruminationService.buildCustomScenarioCandidate(
                userId, wordbookId, "custom_clinic", "社区诊所", "🩺", "看病挂号问诊"))
                .thenReturn(generated);

        AtomicBoolean stepOneDone = new AtomicBoolean(false);
        AtomicReference<RuminationScenarioCheckpoint> checkpointRef = new AtomicReference<>();

        doAnswer(inv -> {
            checkpointRef.set(inv.getArgument(2));
            return null;
        }).when(executionService).saveStepCheckpoint(eq(taskId), eq(RuminationScenarioTaskHandler.STEP_GENERATE), any());

        when(executionService.getStepCheckpoint(taskId, RuminationScenarioTaskHandler.STEP_GENERATE, RuminationScenarioCheckpoint.class))
                .thenAnswer(inv -> checkpointRef.get());

        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> {
                    String stepCode = inv.getArgument(1);
                    Supplier<?> supplier = inv.getArgument(4);
                    if (RuminationScenarioTaskHandler.STEP_GENERATE.equals(stepCode)) {
                        if (stepOneDone.get()) return null;
                        Object res = supplier.get();
                        stepOneDone.set(true);
                        return res;
                    }
                    return supplier.get();
                });

        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "custom_clinic")).thenReturn(null);
        when(graphMapper.insert(any(LearningRuminationGraph.class)))
                .thenThrow(new DataRetrievalFailureException("simulated DB failure"))
                .thenAnswer(inv -> {
                    LearningRuminationGraph g = inv.getArgument(0);
                    g.setId(9101L);
                    return 1;
                });

        assertThatThrownBy(() -> handler.execute(task, payload))
                .isInstanceOf(DataRetrievalFailureException.class);
        assertThat(stepOneDone.get()).isTrue();

        handler.execute(task, payload);

        verify(ruminationService, times(1)).buildCustomScenarioCandidate(any(), any(), any(), any(), any(), any());
        verify(taskService).bindBusiness(taskId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, "9101");
        verify(taskService).complete(taskId, AiTaskConstants.STATUS_COMPLETED, null);
    }
}
