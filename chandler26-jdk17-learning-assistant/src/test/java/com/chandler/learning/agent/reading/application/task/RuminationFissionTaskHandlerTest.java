package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节点 4D 衍生裂变两阶段异步处理器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationFissionTaskHandlerTest {

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
    private RuminationFissionTaskHandler handler;

    @BeforeEach
    void setUp() {
        graphPersistenceService = new RuminationGraphPersistenceService(
                graphMapper, semanticEvaluator, objectMapper);
        handler = new RuminationFissionTaskHandler(
                ruminationService, graphPersistenceService, executionService, taskService);
    }

    @Test
    @DisplayName("落库前重检父节点：若原场景中的父节点已被删除，安全跳过写库避免写入孤立节点与悬空连线")
    void execute_parentDeletedBeforePersist_skipsGraphMutationCleanly() throws Exception {
        Long taskId = 8201L;
        Long userId = 1001L;
        Long wordbookId = 2001L;

        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "produce_kitchen",
                "scenarioName", "生鲜采购与厨房料理",
                "parentNodeId", "core-deleted-turnip",
                "targetWord", "turnip",
                "targetDefinition", "芜菁",
                "existingTerms", List.of("apple", "turnip"),
                "modelConfigId", 301L
        );

        RuminationNodeResponse candNode = new RuminationNodeResponse();
        candNode.setId("exp-dice");
        candNode.setNodeType("expansion");
        candNode.setTerm("dice");
        RuminationEdgeResponse candEdge = new RuminationEdgeResponse();
        candEdge.setSource("core-deleted-turnip");
        candEdge.setTarget("exp-dice");

        RuminationFissionCheckpoint checkpoint = new RuminationFissionCheckpoint(
                wordbookId, "produce_kitchen", "生鲜采购与厨房料理",
                "core-deleted-turnip", "turnip", "ai", List.of(candNode), List.of(candEdge));
        when(ruminationService.buildFissionCandidate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(checkpoint);

        // 原场景当前数据库中已无 core-deleted-turnip
        RuminationNodeResponse hub = new RuminationNodeResponse();
        hub.setId("hub");
        hub.setNodeType("hub");
        RuminationNodeResponse apple = new RuminationNodeResponse();
        apple.setId("core-apple");
        apple.setNodeType("core");
        apple.setTerm("apple");

        LearningRuminationGraph existingGraph = new LearningRuminationGraph();
        existingGraph.setId(7001L);
        existingGraph.setGraphNodesJson(objectMapper.writeValueAsString(List.of(hub, apple)));
        existingGraph.setGraphEdgesJson("[]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen"))
                .thenReturn(existingGraph);

        AtomicReference<RuminationFissionResponse> step2ResultRef = new AtomicReference<>();
        doAnswer(inv -> {
            if (RuminationFissionTaskHandler.STEP_PERSIST.equals(inv.getArgument(1))) {
                step2ResultRef.set(inv.getArgument(2));
            }
            return null;
        }).when(executionService).saveStepCheckpoint(eq(taskId), any(), any());

        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get());

        handler.execute(task, payload);

        verify(graphMapper, never()).updateById(any(LearningRuminationGraph.class));
        assertThat(step2ResultRef.get()).isNotNull();
        assertThat(step2ResultRef.get().getApplyStatus()).isEqualTo("skipped_parent_missing");
        assertThat(step2ResultRef.get().getNodes()).isEmpty();
        verify(taskService).complete(taskId, AiTaskConstants.STATUS_COMPLETED, null);
    }

    @Test
    @DisplayName("父节点仍存在时：仅增量写入原场景星系图谱并记录 applied 结果")
    void execute_parentStillExists_persistsNewExpansionsToOriginalScenario() throws Exception {
        Long taskId = 8202L;
        Long userId = 1001L;
        Long wordbookId = 2001L;

        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "produce_kitchen",
                "scenarioName", "生鲜采购与厨房料理",
                "parentNodeId", "core-carrot",
                "targetWord", "carrot"
        );

        RuminationNodeResponse candNode = new RuminationNodeResponse();
        candNode.setId("exp-shred");
        candNode.setNodeType("expansion");
        candNode.setTerm("shred");
        candNode.setDimension("action");
        RuminationEdgeResponse candEdge = new RuminationEdgeResponse();
        candEdge.setSource("core-carrot");
        candEdge.setTarget("exp-shred");
        candEdge.setRelationType("action");

        RuminationFissionCheckpoint checkpoint = new RuminationFissionCheckpoint(
                wordbookId, "produce_kitchen", "生鲜采购与厨房料理",
                "core-carrot", "carrot", "fallback", List.of(candNode), List.of(candEdge));
        when(ruminationService.buildFissionCandidate(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(checkpoint);

        RuminationNodeResponse hub = new RuminationNodeResponse();
        hub.setId("hub");
        hub.setNodeType("hub");
        RuminationNodeResponse carrot = new RuminationNodeResponse();
        carrot.setId("core-carrot");
        carrot.setNodeType("core");
        carrot.setTerm("carrot");

        LearningRuminationGraph existingGraph = new LearningRuminationGraph();
        existingGraph.setId(7002L);
        existingGraph.setGraphNodesJson(objectMapper.writeValueAsString(List.of(hub, carrot)));
        existingGraph.setGraphEdgesJson("[]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen"))
                .thenReturn(existingGraph);

        AtomicReference<RuminationFissionResponse> step2ResultRef = new AtomicReference<>();
        doAnswer(inv -> {
            if (RuminationFissionTaskHandler.STEP_PERSIST.equals(inv.getArgument(1))) {
                step2ResultRef.set(inv.getArgument(2));
            }
            return null;
        }).when(executionService).saveStepCheckpoint(eq(taskId), any(), any());

        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get());

        handler.execute(task, payload);

        verify(graphMapper).updateById(existingGraph);
        assertThat(existingGraph.getGraphNodesJson()).contains("shred");
        assertThat(step2ResultRef.get().getApplyStatus()).isEqualTo("applied");
        assertThat(step2ResultRef.get().getAiPowered()).isFalse();
        assertThat(step2ResultRef.get().getNodes()).hasSize(1);
    }
}
