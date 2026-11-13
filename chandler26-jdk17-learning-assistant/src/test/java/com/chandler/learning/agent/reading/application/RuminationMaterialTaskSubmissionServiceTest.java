package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationMaterialGenerateRequest;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识反刍材料异步提交服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationMaterialTaskSubmissionServiceTest {

    @Mock
    private WordbookService wordbookService;
    @Mock
    private RuminationService ruminationService;
    @Mock
    private RuminationAiGenerator aiGenerator;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RuminationMaterialTaskSubmissionService submissionService;

    private final Long userId = 1001L;
    private final Long wordbookId = 2001L;
    private LearningWordbook wordbook;

    @BeforeEach
    void setUp() {
        submissionService = new RuminationMaterialTaskSubmissionService(
                wordbookService, ruminationService, aiGenerator, aiAsyncTaskService, objectMapper);
        wordbook = new LearningWordbook();
        wordbook.setId(wordbookId);
        wordbook.setUserId(userId);
        wordbook.setName("默认词本");
    }

    @Test
    @DisplayName("去重后目标词不足 5 个时拒绝提交并禁止建单")
    void submitGenerateTask_fewerThanFiveDistinctWords_throwsBadRequest() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.normalizeDistinctTargetWords(any())).thenReturn(List.of("peel", "potato"));

        RuminationMaterialGenerateRequest req = new RuminationMaterialGenerateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setTargetWords(List.of("peel", "PEEL", "potato"));

        assertThatThrownBy(() -> submissionService.submitGenerateTask(userId, req))
                .isInstanceOf(LearningAssistantException.class);
        verify(aiAsyncTaskService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("同一次请求重发（相同 requestId）直接复用已有任务不重复创建")
    void submitGenerateTask_duplicateRequestId_reusesExistingTask() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.normalizeDistinctTargetWords(any()))
                .thenReturn(List.of("peel", "potato", "onion", "carrot", "boil"));
        when(ruminationService.resolveOwnedScenarioMeta(userId, wordbookId, "produce_kitchen"))
                .thenReturn(new String[]{"生鲜采购与厨房料理", "生鲜采购与厨房料理日常情境与交流"});

        AiAsyncTask existing = new AiAsyncTask();
        existing.setId(9011L);
        existing.setStatus(AiTaskConstants.STATUS_RUNNING);
        AiAsyncTaskResponse expectedRes = new AiAsyncTaskResponse();
        expectedRes.setId(9011L);
        expectedRes.setStatus(AiTaskConstants.STATUS_RUNNING);

        when(aiAsyncTaskService.findLatestByIdempotencyKey(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL), eq("rum_mat_req:1001:req-abc-1")))
                .thenReturn(existing);
        when(aiAsyncTaskService.toDetailResponse(existing)).thenReturn(expectedRes);

        RuminationMaterialGenerateRequest req = new RuminationMaterialGenerateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setTargetWords(List.of("peel", "potato", "onion", "carrot", "boil"));
        req.setRequestId("req-abc-1");

        AiAsyncTaskResponse res = submissionService.submitGenerateTask(userId, req);

        assertThat(res.getId()).isEqualTo(9011L);
        verify(aiAsyncTaskService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("正常提交保存场景描述、词袋与释义快照到任务载荷并立即返回 taskId")
    @SuppressWarnings("unchecked")
    void submitGenerateTask_validRequest_persistsSnapshotPayloadAndReturnsTask() {
        List<String> normalizedWords = List.of("peel", "potato", "onion", "carrot", "boil");
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.normalizeDistinctTargetWords(any())).thenReturn(normalizedWords);
        when(ruminationService.resolveOwnedScenarioMeta(userId, wordbookId, "produce_kitchen"))
                .thenReturn(new String[]{"生鲜采购与厨房料理", "厨房场景表达"});
        when(ruminationService.snapshotWordDefinitions(userId, wordbookId, normalizedWords))
                .thenReturn(Map.of("peel", "削皮", "potato", "土豆"));
        when(aiGenerator.resolveDefaultModelConfigId()).thenReturn(88L);

        AiAsyncTask created = new AiAsyncTask();
        created.setId(9022L);
        created.setStatus(AiTaskConstants.STATUS_PENDING);
        AiAsyncTaskResponse expectedRes = new AiAsyncTaskResponse();
        expectedRes.setId(9022L);
        expectedRes.setStatus(AiTaskConstants.STATUS_PENDING);

        when(aiAsyncTaskService.create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), any()))
                .thenReturn(created);
        when(aiAsyncTaskService.toDetailResponse(created)).thenReturn(expectedRes);

        RuminationMaterialGenerateRequest req = new RuminationMaterialGenerateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setTargetWords(normalizedWords);

        AiAsyncTaskResponse res = submissionService.submitGenerateTask(userId, req);

        assertThat(res.getId()).isEqualTo(9022L);
        assertThat(res.getStatus()).isEqualTo(AiTaskConstants.STATUS_PENDING);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiAsyncTaskService).create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("scenarioCode")).isEqualTo("produce_kitchen");
        assertThat(payloadCaptor.getValue().get("scenarioName")).isEqualTo("生鲜采购与厨房料理");
        assertThat(payloadCaptor.getValue().get("scenarioPrompt")).isEqualTo("厨房场景表达");
        assertThat(payloadCaptor.getValue().get("targetWords")).isEqualTo(normalizedWords);
        assertThat(payloadCaptor.getValue().get("consumeBag")).isEqualTo(true);
        assertThat(payloadCaptor.getValue().get("modelConfigId")).isEqualTo(88L);
    }

    @Test
    @DisplayName("基于历史材料重新生成：严格校验材料归属并在任务载荷写入 consumeBag=false")
    @SuppressWarnings("unchecked")
    void submitRegenerateTask_validOwnedMaterial_persistsConsumeBagFalse() {
        List<String> normalizedWords = List.of("peel", "potato", "onion", "carrot", "boil");
        LearningRuminationMaterial source = new LearningRuminationMaterial();
        source.setId(5501L);
        source.setUserId(userId);
        source.setWordbookId(wordbookId);
        source.setScenarioCode("produce_kitchen");
        source.setTargetWordsJson("[\"peel\",\"potato\",\"onion\",\"carrot\",\"boil\"]");

        when(ruminationService.requireOwnedMaterial(userId, 5501L)).thenReturn(source);
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.readMaterialTargetWords(source)).thenReturn(normalizedWords);
        when(ruminationService.resolveOwnedScenarioMeta(userId, wordbookId, "produce_kitchen"))
                .thenReturn(new String[]{"生鲜采购与厨房料理", "厨房场景表达"});
        when(ruminationService.snapshotWordDefinitions(userId, wordbookId, normalizedWords))
                .thenReturn(Map.of("peel", "削皮"));
        when(aiGenerator.resolveDefaultModelConfigId()).thenReturn(88L);

        AiAsyncTask created = new AiAsyncTask();
        created.setId(9033L);
        created.setStatus(AiTaskConstants.STATUS_PENDING);
        AiAsyncTaskResponse expectedRes = new AiAsyncTaskResponse();
        expectedRes.setId(9033L);
        expectedRes.setStatus(AiTaskConstants.STATUS_PENDING);

        when(aiAsyncTaskService.create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2), any(), any()))
                .thenReturn(created);
        when(aiAsyncTaskService.toDetailResponse(created)).thenReturn(expectedRes);

        AiAsyncTaskResponse res = submissionService.submitRegenerateTask(userId, 5501L, "regen-req-1");

        assertThat(res.getId()).isEqualTo(9033L);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(aiAsyncTaskService).create(
                eq(userId), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL), any(),
                isNull(), isNull(), eq(wordbookId),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE), isNull(),
                eq(AiTaskConstants.DEFAULT_PRIORITY), eq(2),
                eq("rum_mat_req:1001:regen-req-1"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().get("consumeBag")).isEqualTo(false);
        assertThat(payloadCaptor.getValue().get("sourceMaterialId")).isEqualTo("5501");
        assertThat(payloadCaptor.getValue().get("targetWords")).isEqualTo(normalizedWords);
    }

    @Test
    @DisplayName("基于历史材料重新生成：当前已有同源活动任务时复用原任务，避免并发重复扣费")
    void submitRegenerateTask_activeTaskExists_reusesActiveTask() {
        LearningRuminationMaterial source = new LearningRuminationMaterial();
        source.setId(5502L);
        source.setUserId(userId);
        source.setWordbookId(wordbookId);
        source.setScenarioCode("produce_kitchen");

        when(ruminationService.requireOwnedMaterial(userId, 5502L)).thenReturn(source);
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(ruminationService.readMaterialTargetWords(source))
                .thenReturn(List.of("peel", "potato", "onion", "carrot", "boil"));
        when(ruminationService.resolveOwnedScenarioMeta(userId, wordbookId, "produce_kitchen"))
                .thenReturn(new String[]{"生鲜采购与厨房料理", "厨房场景表达"});

        AiAsyncTask active = new AiAsyncTask();
        active.setId(9044L);
        active.setStatus(AiTaskConstants.STATUS_RUNNING);
        AiAsyncTaskResponse activeRes = new AiAsyncTaskResponse();
        activeRes.setId(9044L);
        activeRes.setStatus(AiTaskConstants.STATUS_RUNNING);

        when(aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, null, "rum_mat_regen:1001:5502"))
                .thenReturn(active);
        when(aiAsyncTaskService.toDetailResponse(active)).thenReturn(activeRes);

        AiAsyncTaskResponse res = submissionService.submitRegenerateTask(userId, 5502L, "regen-req-2");

        assertThat(res.getId()).isEqualTo(9044L);
        verify(aiAsyncTaskService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("基于历史材料重新生成：材料不存在或越权访问时拒绝提交")
    void submitRegenerateTask_unownedMaterial_throwsNotFound() {
        when(ruminationService.requireOwnedMaterial(userId, 9999L))
                .thenThrow(LearningAssistantException.notFound(
                        com.chandler.learning.agent.common.exception.LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND,
                        "场景材料文章不存在或无权访问"));

        assertThatThrownBy(() -> submissionService.submitRegenerateTask(userId, 9999L, "regen-unauth"))
                .isInstanceOf(LearningAssistantException.class);
        verify(aiAsyncTaskService, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
