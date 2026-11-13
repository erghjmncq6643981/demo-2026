package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.application.RuminationAiGenerator;
import com.chandler.learning.agent.reading.application.RuminationAudioService;
import com.chandler.learning.agent.reading.application.RuminationMaterialPersistenceService;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationSentencePracticeMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识反刍场景材料两阶段异步处理器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationMaterialTaskHandlerTest {

    @Mock
    private RuminationAiGenerator aiGenerator;
    @Mock
    private AiTaskExecutionService executionService;
    @Mock
    private AiAsyncTaskService taskService;
    @Mock
    private LearningRuminationMaterialMapper materialMapper;
    @Mock
    private LearningRuminationProbeMapper probeMapper;
    @Mock
    private LearningRuminationGraphMapper graphMapper;
    @Mock
    private LearningRuminationSentencePracticeMapper sentencePracticeMapper;
    @Mock
    private RuminationAudioService ruminationAudioService;

    private RuminationMaterialPersistenceService persistenceService;
    private RuminationMaterialTaskHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Long userId = 1001L;
    private final Long wordbookId = 2001L;
    private final Long taskId = 8001L;

    @BeforeEach
    void setUp() {
        persistenceService = new RuminationMaterialPersistenceService(
                materialMapper, probeMapper, graphMapper, sentencePracticeMapper, objectMapper);
        handler = new RuminationMaterialTaskHandler(
                aiGenerator, persistenceService, executionService, taskService, ruminationAudioService);
    }

    @Test
    @DisplayName("阶段 2 落库失败后重试：跳过阶段 1 模型调用并直接复用检查点完成落库与词袋合并")
    void execute_stepTwoFailureThenRetry_skipsModelAndMergesSnapshotWordsOnly() {
        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "produce_kitchen",
                "scenarioName", "生鲜采购与厨房料理",
                "scenarioPrompt", "生鲜采购与厨房料理日常情境与交流",
                "targetWords", List.of("peel", "potato", "onion", "carrot", "boil"),
                "wordDefinitions", Map.of("peel", "削皮"),
                "modelConfigId", 301L
        );

        RuminationAiGenerator.GeneratedMaterialResult aiResult = new RuminationAiGenerator.GeneratedMaterialResult(
                "Fresh Prep",
                "生鲜厨房准备场景",
                "Please wash each potato, slice onion, clean carrot, peel ginger, and boil fresh water.",
                "请洗净土豆、切洋葱、洗胡萝卜、削姜皮并煮开清水。",
                List.of(new RuminationAiGenerator.ProbeDraft(
                        "peel", "context_match", "Q?", List.of("A", "B", "C", "D"), "A", "提示"
                )),
                false // 降级分支验证 fallback 来源落库
        );
        when(aiGenerator.generateMaterialAndProbes(eq(userId), eq("生鲜采购与厨房料理"), any(), any(), any()))
                .thenReturn(aiResult);

        AtomicBoolean stepOneCompleted = new AtomicBoolean(false);
        AtomicReference<RuminationMaterialCheckpoint> checkpointStore = new AtomicReference<>();

        doAnswer(inv -> {
            checkpointStore.set(inv.getArgument(2));
            return null;
        }).when(executionService).saveStepCheckpoint(eq(taskId), eq(RuminationMaterialTaskHandler.STEP_GENERATE), any());

        when(executionService.getStepCheckpoint(taskId, RuminationMaterialTaskHandler.STEP_GENERATE, RuminationMaterialCheckpoint.class))
                .thenAnswer(inv -> checkpointStore.get());

        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> {
                    String stepCode = inv.getArgument(1);
                    Supplier<?> supplier = inv.getArgument(4);
                    if (RuminationMaterialTaskHandler.STEP_GENERATE.equals(stepCode)) {
                        if (stepOneCompleted.get()) {
                            return null; // 已完成步骤跳过执行
                        }
                        Object out = supplier.get();
                        stepOneCompleted.set(true);
                        return out;
                    }
                    return supplier.get();
                });

        // 首次执行阶段 2 模拟数据库插入异常
        when(materialMapper.insert(any(LearningRuminationMaterial.class)))
                .thenThrow(new DataRetrievalFailureException("simulated DB failure"))
                .thenAnswer(inv -> {
                    LearningRuminationMaterial m = inv.getArgument(0);
                    m.setId(9901L);
                    return 1;
                });

        assertThatThrownBy(() -> handler.execute(task, payload))
                .isInstanceOf(DataRetrievalFailureException.class);
        assertThat(stepOneCompleted.get()).isTrue();
        assertThat(checkpointStore.get()).isNotNull();

        // 模拟等待期间用户新加入词袋的词汇 harvest
        LearningRuminationGraph graph = new LearningRuminationGraph();
        graph.setId(7701L);
        graph.setBagTermsJson("[\"peel\",\"potato\",\"harvest\"]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(graph);

        // 重试执行：阶段 1 必须跳过，不能再调用模型
        handler.execute(task, payload);

        verify(aiGenerator, times(1)).generateMaterialAndProbes(any(), any(), any(), any(), any());
        verify(probeMapper, times(1)).insert(any(LearningRuminationProbe.class));
        assertThat(graph.getBagTermsJson()).isEqualTo("[\"harvest\"]");

        ArgumentCaptor<LearningRuminationProbe> probeCaptor = ArgumentCaptor.forClass(LearningRuminationProbe.class);
        verify(probeMapper).insert(probeCaptor.capture());
        assertThat(probeCaptor.getValue().getGenerationSource()).isEqualTo("fallback");

        ArgumentCaptor<LearningRuminationMaterial> matCaptor = ArgumentCaptor.forClass(LearningRuminationMaterial.class);
        verify(materialMapper, times(2)).insert(matCaptor.capture());
        assertThat(matCaptor.getAllValues().get(1).getGenerationSource()).isEqualTo("fallback");
        assertThat(matCaptor.getAllValues().get(1).getTaskId()).isEqualTo(taskId);

        verify(taskService).bindBusiness(taskId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, "9901");
        verify(taskService).complete(taskId, AiTaskConstants.STATUS_COMPLETED, null);
    }

    @Test
    @DisplayName("重新生成任务 consumeBag=false：原位覆盖 sourceMaterialId 对应材料、清理旧探针跟敲与音频缓存，且不扣除词袋")
    void execute_consumeBagFalse_overwritesSourceMaterialAndDoesNotDeductGraphBag() {
        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "produce_kitchen",
                "scenarioName", "生鲜采购与厨房料理",
                "scenarioPrompt", "生鲜采购与厨房料理日常情境与交流",
                "targetWords", List.of("peel", "potato", "onion", "carrot", "boil"),
                "wordDefinitions", Map.of("peel", "削皮"),
                "consumeBag", false,
                "sourceMaterialId", "5501"
        );

        LearningRuminationMaterial existingMaterial = new LearningRuminationMaterial();
        existingMaterial.setId(5501L);
        existingMaterial.setUserId(userId);
        existingMaterial.setWordbookId(wordbookId);
        existingMaterial.setScenarioCode("produce_kitchen");
        existingMaterial.setTitle("Old Material Title");
        existingMaterial.setAudioStatus("completed");
        existingMaterial.setAudioPath("rumination/5501.mp3");

        RuminationAiGenerator.GeneratedMaterialResult aiResult = new RuminationAiGenerator.GeneratedMaterialResult(
                "Fresh Table Story",
                "生鲜采购重编语境",
                "We peel a fresh potato, slice an onion and carrot, then boil soup.",
                "我们削了一个新鲜土豆，切了洋葱和胡萝卜，然后煮汤。",
                List.of(new RuminationAiGenerator.ProbeDraft(
                        "peel", "context_match", "What does the cook peel first?",
                        List.of("A fresh potato", "An iron pot", "A wooden spoon", "A glass jar"),
                        "A fresh potato", "focus on the first step", "ai"
                )),
                true
        );
        when(aiGenerator.generateMaterialAndProbes(eq(userId), eq("生鲜采购与厨房料理"), any(), any(), any()))
                .thenReturn(aiResult);
        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get());
        // 第 1 次查 taskId 幂等记录返回 null；第 2 次查 sourceMaterialId 返回待覆盖原记录
        when(materialMapper.selectOne(any())).thenReturn(null, existingMaterial);

        handler.execute(task, payload);

        verify(materialMapper, never()).insert(any(LearningRuminationMaterial.class));
        verify(materialMapper).updateById(existingMaterial);
        verify(materialMapper).update(eq(null), any());
        verify(probeMapper).delete(any());
        verify(sentencePracticeMapper).deleteByMaterialId(userId, 5501L);
        verify(probeMapper, times(1)).insert(any(LearningRuminationProbe.class));
        verify(ruminationAudioService).invalidateStoredAudio(5501L);
        assertThat(existingMaterial.getTitle()).isEqualTo("Fresh Table Story");
        assertThat(existingMaterial.getAudioStatus()).isEqualTo("none");
        assertThat(existingMaterial.getAudioPath()).isNull();
        assertThat(existingMaterial.getTaskId()).isEqualTo(taskId);

        verify(graphMapper, never()).selectByScenarioForUpdate(any(), any(), any());
        verify(graphMapper, never()).updateById(any(LearningRuminationGraph.class));
        verify(taskService).bindBusiness(taskId, AiTaskConstants.TYPE_RUMINATION_MATERIAL, "5501");
        verify(taskService).complete(taskId, AiTaskConstants.STATUS_COMPLETED, null);
    }

    @Test
    @DisplayName("覆盖重新生成时若原场景材料不存在或已删除：抛出 NOT_FOUND 异常且不插入新记录")
    void execute_overwriteMissingSourceMaterial_throwsNotFound() {
        AiAsyncTask task = new AiAsyncTask();
        task.setId(taskId);
        task.setOwnerUserId(userId);
        task.setOperatorUserId(userId);

        Map<String, Object> payload = Map.of(
                "wordbookId", String.valueOf(wordbookId),
                "scenarioCode", "produce_kitchen",
                "scenarioName", "生鲜采购与厨房料理",
                "targetWords", List.of("peel", "potato", "onion", "carrot", "boil"),
                "consumeBag", false,
                "sourceMaterialId", "5599"
        );

        RuminationAiGenerator.GeneratedMaterialResult aiResult = new RuminationAiGenerator.GeneratedMaterialResult(
                "Fresh Table Story",
                "summary",
                "We peel a fresh potato, slice an onion and carrot, then boil soup.",
                "翻译",
                List.of(),
                true
        );
        when(aiGenerator.generateMaterialAndProbes(eq(userId), eq("生鲜采购与厨房料理"), any(), any(), any()))
                .thenReturn(aiResult);
        when(executionService.execute(eq(taskId), any(), eq(userId), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(4)).get());
        when(materialMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> handler.execute(task, payload))
                .isInstanceOf(LearningAssistantException.class)
                .satisfies(ex -> assertThat(((LearningAssistantException) ex).getErrorCode())
                        .isEqualTo(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND.getCode()));
        verify(materialMapper, never()).insert(any(LearningRuminationMaterial.class));
    }
}
