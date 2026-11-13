package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.request.RuminationCustomScenarioRequest;
import com.chandler.learning.agent.reading.api.request.RuminationFissionRequest;
import com.chandler.learning.agent.reading.api.response.RuminationAsyncTaskItemResponse;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch 2 自定义场景与节点裂变异步任务提交及跨场景追踪服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationGraphTaskSubmissionServiceTest {

    @Mock
    private WordbookService wordbookService;
    @Mock
    private RuminationService ruminationService;
    @Mock
    private RuminationAiGenerator aiGenerator;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;
    @Mock
    private AiTaskExecutionService executionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuminationGraphTaskSubmissionService submissionService;

    private final Long userId = 1001L;
    private final Long wordbookId = 2001L;
    private LearningWordbook wordbook;

    @BeforeEach
    void setUp() {
        submissionService = new RuminationGraphTaskSubmissionService(
                wordbookService, ruminationService, aiGenerator,
                aiAsyncTaskService, executionService, objectMapper);
        wordbook = new LearningWordbook();
        wordbook.setId(wordbookId);
        wordbook.setUserId(userId);
        wordbook.setName("核心词本");
    }

    @Test
    @DisplayName("提交自定义场景异步任务应保存场景元数据快照并立即返回 taskId")
    @SuppressWarnings("unchecked")
    void submitCustomScenarioTask_savesSnapshotAndReturnsTask() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(aiGenerator.resolveDefaultModelConfigId()).thenReturn(77L);

        AiAsyncTask created = new AiAsyncTask();
        created.setId(9501L);
        created.setStatus(AiTaskConstants.STATUS_PENDING);
        AiAsyncTaskResponse resDto = new AiAsyncTaskResponse();
        resDto.setId(9501L);
        resDto.setStatus(AiTaskConstants.STATUS_PENDING);

        when(aiAsyncTaskService.create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_SCENARIO), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), any()))
                .thenReturn(created);
        when(aiAsyncTaskService.toDetailResponse(created)).thenReturn(resDto);

        RuminationCustomScenarioRequest req = new RuminationCustomScenarioRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("custom_airport");
        req.setScenarioName("机场登机");
        req.setScenarioIcon("✈️");
        req.setScenarioPrompt("机场值机与安检登机");

        AiAsyncTaskResponse res = submissionService.submitCustomScenarioTask(userId, req);

        assertThat(res.getId()).isEqualTo(9501L);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiAsyncTaskService).create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_SCENARIO), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("scenarioCode")).isEqualTo("custom_airport");
        assertThat(payloadCaptor.getValue().get("scenarioPrompt")).isEqualTo("机场值机与安检登机");
        assertThat(payloadCaptor.getValue().get("modelConfigId")).isEqualTo(77L);
    }

    @Test
    @DisplayName("提交节点 4D 衍生裂变异步任务应校验并绑定原词本、原场景与父节点快照")
    @SuppressWarnings("unchecked")
    void submitFissionTask_bindsWordbookScenarioAndParentNode() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.resolveOwnedScenarioMeta(userId, wordbookId, "produce_kitchen"))
                .thenReturn(new String[]{"生鲜采购与厨房料理", "厨房料理语境"});
        when(aiGenerator.resolveDefaultModelConfigId()).thenReturn(77L);

        AiAsyncTask created = new AiAsyncTask();
        created.setId(9601L);
        created.setStatus(AiTaskConstants.STATUS_PENDING);
        AiAsyncTaskResponse resDto = new AiAsyncTaskResponse();
        resDto.setId(9601L);
        resDto.setStatus(AiTaskConstants.STATUS_PENDING);

        when(aiAsyncTaskService.create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_FISSION), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), any()))
                .thenReturn(created);
        when(aiAsyncTaskService.toDetailResponse(created)).thenReturn(resDto);

        RuminationFissionRequest req = new RuminationFissionRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setParentNodeId("core-carrot");
        req.setTargetWord("carrot");
        req.setExistingTerms(List.of("carrot", "apple"));

        AiAsyncTaskResponse res = submissionService.submitFissionTask(userId, req);

        assertThat(res.getId()).isEqualTo(9601L);
        verify(ruminationService).assertParentNodeExistsIfSaved(userId, wordbookId, "produce_kitchen", "core-carrot");
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiAsyncTaskService).create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_FISSION), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("wordbookId")).isEqualTo(String.valueOf(wordbookId));
        assertThat(payloadCaptor.getValue().get("scenarioCode")).isEqualTo("produce_kitchen");
        assertThat(payloadCaptor.getValue().get("parentNodeId")).isEqualTo("core-carrot");
    }

    @Test
    @DisplayName("切换场景后查询任务列表仍应返回另一场景处于活动状态的裂变任务，确保不丢失通知")
    void listRuminationTasks_switchedScenario_retainsActiveTaskFromOtherScenario() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);

        AiAsyncTask activeFissionInOrigin = new AiAsyncTask();
        activeFissionInOrigin.setId(9701L);
        activeFissionInOrigin.setTaskType(AiTaskConstants.TYPE_RUMINATION_FISSION);
        activeFissionInOrigin.setStatus(AiTaskConstants.STATUS_RUNNING);
        activeFissionInOrigin.setPayloadJson("{\"scenarioCode\":\"produce_kitchen\",\"scenarioName\":\"生鲜采购与厨房料理\",\"parentNodeId\":\"core-carrot\",\"targetWord\":\"carrot\"}");

        AiAsyncTaskResponse activeRes = new AiAsyncTaskResponse();
        activeRes.setId(9701L);
        activeRes.setTaskType(AiTaskConstants.TYPE_RUMINATION_FISSION);
        activeRes.setStatus(AiTaskConstants.STATUS_RUNNING);

        when(aiAsyncTaskService.findRecentByRelatedJobAndTypes(eq(userId), eq(wordbookId), any(), eq(20)))
                .thenReturn(List.of(activeFissionInOrigin));
        when(aiAsyncTaskService.toDetailResponse(activeFissionInOrigin)).thenReturn(activeRes);

        // 用户已切至 custom_hospital 查看
        List<RuminationAsyncTaskItemResponse> items = submissionService.listRuminationTasks(
                userId, wordbookId, "custom_hospital");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getScenarioCode()).isEqualTo("produce_kitchen");
        assertThat(items.get(0).getParentNodeId()).isEqualTo("core-carrot");
        assertThat(items.get(0).getTargetWord()).isEqualTo("carrot");
    }
}
