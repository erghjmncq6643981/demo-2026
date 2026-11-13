package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationCustomScenarioRequest;
import com.chandler.learning.agent.reading.api.request.RuminationFissionRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphBagUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphPositionsUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphSaveRequest;
import com.chandler.learning.agent.reading.api.request.RuminationMaterialGenerateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationProbeSubmitRequest;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphSaveResponse;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeSubmitResponse;
import com.chandler.learning.agent.reading.api.response.RuminationScenarioResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuminationServiceTest {

    @Mock
    private LearningRuminationGraphMapper graphMapper;
    @Mock
    private LearningRuminationMaterialMapper materialMapper;
    @Mock
    private LearningRuminationProbeMapper probeMapper;
    @Mock
    private WordbookService wordbookService;
    @Mock
    private RuminationSemanticEvaluator semanticEvaluator;
    @Mock
    private RuminationAiGenerator aiGenerator;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RuminationMaterialPersistenceService persistenceService;
    private RuminationGraphPersistenceService graphPersistenceService;
    private RuminationService ruminationService;

    private final Long userId = 1001L;
    private final Long wordbookId = 2001L;
    private final Long materialId = 3001L;
    private final Long probeId = 4001L;

    private LearningWordbook wordbook;
    private LearningRuminationMaterial material;
    private LearningRuminationProbe probe;
    private LearningWordbookEntry entry;

    @BeforeEach
    void setUp() {
        persistenceService = new RuminationMaterialPersistenceService(
                materialMapper, probeMapper, graphMapper, objectMapper);
        graphPersistenceService = new RuminationGraphPersistenceService(
                graphMapper, semanticEvaluator, objectMapper);
        ruminationService = new RuminationService(
                wordbookService, objectMapper, graphMapper, materialMapper,
                probeMapper, semanticEvaluator, aiGenerator,
                persistenceService, graphPersistenceService, aiAsyncTaskService);

        wordbook = new LearningWordbook();
        wordbook.setId(wordbookId);
        wordbook.setUserId(userId);
        wordbook.setName("自考核心词汇");

        material = new LearningRuminationMaterial();
        material.setId(materialId);
        material.setUserId(userId);
        material.setWordbookId(wordbookId);
        material.setScenarioCode("produce_kitchen");

        probe = new LearningRuminationProbe();
        probe.setId(probeId);
        probe.setMaterialId(materialId);
        probe.setUserId(userId);
        probe.setTargetWord("peel");
        probe.setCorrectAnswer("To strip or cut off the outer skin of produce");
        probe.setProbeType("context_match");

        entry = new LearningWordbookEntry();
        entry.setId(5001L);
        entry.setWordbookId(wordbookId);
        entry.setUserId(userId);
        entry.setNormalizedTerm("peel");
        entry.setMasteryScore(60);
        entry.setReviewStage(2);
        entry.setNextReviewTime(LocalDateTime.now().plusDays(2));
    }

    @Test
    @DisplayName("P0: 服务端独立判分 - 正确作答加分并原子条件落库")
    void submitProbe_correctAnswer_scores10AndPersists() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(probeMapper.selectByIdForUpdate(probeId, userId)).thenReturn(probe);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        when(probeMapper.markAnsweredAtomically(eq(probeId), eq(userId),
                eq("To strip or cut off the outer skin of produce"),
                eq(true), eq(10), eq(true), any(LocalDateTime.class))).thenReturn(1);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));

        ReviewSubmitResponse reviewRes = new ReviewSubmitResponse();
        reviewRes.setMasteryScore(70);
        reviewRes.setReviewStage(3);
        reviewRes.setNextReviewTime(LocalDateTime.now().plusDays(4));
        when(wordbookService.submitReview(eq(userId), eq(entry.getId()), any(ReviewSubmitRequest.class))).thenReturn(reviewRes);

        RuminationProbeSubmitRequest request = new RuminationProbeSubmitRequest();
        request.setWordbookId(wordbookId);
        request.setProbeId(probeId);
        request.setSelectedAnswer("To strip or cut off the outer skin of produce"); // 正确答案

        RuminationProbeSubmitResponse response = ruminationService.submitProbe(userId, request);

        assertThat(response.getIsCorrect()).isTrue();
        assertThat(response.getScoreGained()).isEqualTo(10);
        assertThat(response.getAlreadyAnswered()).isFalse();
        assertThat(response.getMasteryScoreAfter()).isEqualTo(70);

        verify(probeMapper).markAnsweredAtomically(
                eq(probeId), eq(userId),
                eq("To strip or cut off the outer skin of produce"),
                eq(true), eq(10), eq(true), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("P0: 服务端独立判分 - 前端传 isCorrect=true 但答案错误时，服务端必须判定为错误")
    void submitProbe_wrongAnswer_evenIfFrontendClaimsCorrect_serverScoresZero() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(probeMapper.selectByIdForUpdate(probeId, userId)).thenReturn(probe);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        when(probeMapper.markAnsweredAtomically(eq(probeId), eq(userId),
                eq("Wrong distractor answer"),
                eq(false), eq(0), eq(false), any(LocalDateTime.class))).thenReturn(1);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));

        ReviewSubmitResponse reviewRes = new ReviewSubmitResponse();
        reviewRes.setMasteryScore(60);
        reviewRes.setReviewStage(1);
        when(wordbookService.submitReview(eq(userId), eq(entry.getId()), any(ReviewSubmitRequest.class))).thenReturn(reviewRes);

        RuminationProbeSubmitRequest request = new RuminationProbeSubmitRequest();
        request.setWordbookId(wordbookId);
        request.setProbeId(probeId);
        request.setSelectedAnswer("Wrong distractor answer");
        request.setIsCorrect(true); // 恶意或造假的前端请求

        RuminationProbeSubmitResponse response = ruminationService.submitProbe(userId, request);

        // 服务端独立判定必须为 false，加分必须为 0
        assertThat(response.getIsCorrect()).isFalse();
        assertThat(response.getScoreGained()).isEqualTo(0);

        ArgumentCaptor<ReviewSubmitRequest> reviewCaptor = ArgumentCaptor.forClass(ReviewSubmitRequest.class);
        verify(wordbookService).submitReview(eq(userId), eq(entry.getId()), reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getCheckResult()).isEqualTo("wrong");
        assertThat(reviewCaptor.getValue().getResult()).isEqualTo("vague");
    }

    @Test
    @DisplayName("P0: 重复结算保护 - 探针已作答时幂等返回，严禁二次调用 submitReview")
    void submitProbe_duplicateSubmission_idempotentWithoutReReview() {
        // 探针已作答
        probe.setAnsweredTime(LocalDateTime.now().minusHours(1));
        probe.setIsCorrect(true);
        probe.setSelectedAnswer("To strip or cut off the outer skin of produce");

        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(probeMapper.selectByIdForUpdate(probeId, userId)).thenReturn(probe);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));

        RuminationProbeSubmitRequest request = new RuminationProbeSubmitRequest();
        request.setWordbookId(wordbookId);
        request.setProbeId(probeId);
        request.setSelectedAnswer("To strip or cut off the outer skin of produce");

        RuminationProbeSubmitResponse response = ruminationService.submitProbe(userId, request);

        assertThat(response.getAlreadyAnswered()).isTrue();
        assertThat(response.getScoreGained()).isEqualTo(0);
        assertThat(response.getIsCorrect()).isTrue();

        // 绝不能二次更新词本与探针
        verify(wordbookService, never()).submitReview(any(), any(), any());
        verify(probeMapper, never()).markAnsweredAtomically(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("P1: 并发作答抢占失败时（条件更新返回 0），必须幂等返回且禁止重复结算")
    void submitProbe_concurrentRaceLost_returnsIdempotentAndSkipsReview() {
        LearningRuminationProbe answeredWinner = new LearningRuminationProbe();
        answeredWinner.setId(probeId);
        answeredWinner.setMaterialId(materialId);
        answeredWinner.setUserId(userId);
        answeredWinner.setTargetWord("peel");
        answeredWinner.setCorrectAnswer("To strip or cut off the outer skin of produce");
        answeredWinner.setIsCorrect(true);
        answeredWinner.setAnsweredTime(LocalDateTime.now());

        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(probeMapper.selectByIdForUpdate(probeId, userId))
                .thenReturn(probe)
                .thenReturn(answeredWinner);
        when(materialMapper.selectById(materialId)).thenReturn(material);
        when(probeMapper.markAnsweredAtomically(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel"))).thenReturn(List.of(entry));

        RuminationProbeSubmitRequest request = new RuminationProbeSubmitRequest();
        request.setWordbookId(wordbookId);
        request.setProbeId(probeId);
        request.setSelectedAnswer("To strip or cut off the outer skin of produce");

        RuminationProbeSubmitResponse response = ruminationService.submitProbe(userId, request);

        assertThat(response.getAlreadyAnswered()).isTrue();
        assertThat(response.getScoreGained()).isEqualTo(0);
        assertThat(response.getIsCorrect()).isTrue();
        verify(wordbookService, never()).submitReview(any(), any(), any());
    }

    @Test
    @DisplayName("P1: 未作答探针下发响应必须隐藏正确答案，已作答后允许返回回顾")
    void listMaterials_hidesCorrectAnswerUntilAnswered() {
        LearningRuminationProbe answeredProbe = new LearningRuminationProbe();
        answeredProbe.setId(4002L);
        answeredProbe.setMaterialId(materialId);
        answeredProbe.setUserId(userId);
        answeredProbe.setTargetWord("potato");
        answeredProbe.setProbeType("collocation");
        answeredProbe.setQuestion("Q2");
        answeredProbe.setOptionsJson("[\"A\",\"B\"]");
        answeredProbe.setCorrectAnswer("A");
        answeredProbe.setSelectedAnswer("A");
        answeredProbe.setIsCorrect(true);
        answeredProbe.setAnsweredTime(LocalDateTime.now());

        probe.setOptionsJson("[\"To strip or cut off the outer skin of produce\",\"Wrong\"]");

        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        when(probeMapper.selectList(any())).thenReturn(List.of(probe, answeredProbe));

        List<RuminationMaterialResponse> list = ruminationService.listMaterials(userId, wordbookId, "produce_kitchen");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getProbes()).hasSize(2);
        assertThat(list.get(0).getProbes().get(0).getCorrectAnswer()).isNull();
        assertThat(list.get(0).getProbes().get(1).getCorrectAnswer()).isEqualTo("A");
    }

    @Test
    @DisplayName("P0: 题目及归属核验 - 探针不存在或非当前用户探针拒绝提交")
    void submitProbe_unownedOrMissingProbe_throwsException() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(probeMapper.selectByIdForUpdate(probeId, userId)).thenReturn(null);

        RuminationProbeSubmitRequest request = new RuminationProbeSubmitRequest();
        request.setWordbookId(wordbookId);
        request.setProbeId(probeId);

        assertThatThrownBy(() -> ruminationService.submitProbe(userId, request))
                .isInstanceOf(LearningAssistantException.class);
    }

    @Test
    @DisplayName("P1: 自定义场景持久化失败时严禁吞掉异常静默成功")
    void initCustomScenario_dbInsertFails_throwsInternalError() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(graphMapper.selectOne(any())).thenReturn(null);
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "custom_hospital")).thenReturn(null);
        when(wordbookService.listAllOwnedEntries(userId, wordbookId)).thenReturn(List.of());
        when(semanticEvaluator.evaluateRelevance(any(), any(), any())).thenReturn(List.of());
        when(semanticEvaluator.extractDomainKeywords(any(), any())).thenReturn(List.of("fever"));
        when(aiGenerator.generateCustomScenarioUniverse(any(), any(), any(), any(), any()))
                .thenReturn(new RuminationAiGenerator.GeneratedUniverseResult(Map.of(), List.of(), false));

        when(graphMapper.insert(any(LearningRuminationGraph.class))).thenThrow(new RuntimeException("DB Connection Timeout"));

        RuminationCustomScenarioRequest request = new RuminationCustomScenarioRequest();
        request.setWordbookId(wordbookId);
        request.setScenarioCode("custom_hospital");
        request.setScenarioName("综合医院就诊");

        assertThatThrownBy(() -> ruminationService.initCustomScenario(userId, request))
                .isInstanceOf(LearningAssistantException.class);
    }

    @Test
    @DisplayName("Batch 2: 后台存在活动自定义场景任务时，getGraph 返回占位图谱且不重复触发 AI；listScenarios 保留选项")
    void getGraphAndListScenarios_withActiveScenarioTask_returnsPlaceholderWithoutDuplicateAi() {
        AiAsyncTask pendingTask = new AiAsyncTask();
        pendingTask.setId(9801L);
        pendingTask.setTaskType(AiTaskConstants.TYPE_RUMINATION_SCENARIO);
        pendingTask.setStatus(AiTaskConstants.STATUS_RUNNING);
        pendingTask.setPayloadJson("{\"scenarioCode\":\"custom_clinic\",\"scenarioName\":\"社区诊所\",\"scenarioIcon\":\"🩺\"}");

        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(graphMapper.selectOne(any())).thenReturn(null);
        when(aiAsyncTaskService.findLatestByJobAndScenario(
                userId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, wordbookId, "custom_clinic"))
                .thenReturn(pendingTask);

        RuminationGraphResponse graph = ruminationService.getGraph(userId, wordbookId, "custom_clinic");
        assertThat(graph.getBuilding()).isTrue();
        assertThat(graph.getScenarioName()).isEqualTo("社区诊所");
        assertThat(graph.getNodes()).hasSize(1);
        verify(aiGenerator, never()).generateCustomScenarioUniverse(any(), any(), any(), any(), any());

        when(wordbookService.countMatchingTerms(eq(userId), eq(wordbookId), any(), any())).thenReturn(Map.of());
        when(graphMapper.selectList(any())).thenReturn(List.of());
        when(aiAsyncTaskService.findRecentByRelatedJobAndTypes(
                userId, wordbookId, List.of(AiTaskConstants.TYPE_RUMINATION_SCENARIO), 20))
                .thenReturn(List.of(pendingTask));

        List<RuminationScenarioResponse> scenarios = ruminationService.listScenarios(userId, wordbookId);
        assertThat(scenarios.stream().map(RuminationScenarioResponse::getCode))
                .contains("produce_kitchen", "custom_clinic");
    }

    @Test
    @DisplayName("AI协同: 编织场景材料只扣除提交快照词汇并保留生成期间新增词汇，同时标记生成来源")
    void generateMaterial_callsAiGeneratorAndMergesBagSnapshotPreservingConcurrentAdditions() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(wordbookService.findOwnedEntriesByTerms(eq(userId), eq(wordbookId), any())).thenReturn(List.of(entry));
        when(semanticEvaluator.extractEntryDefinition(entry)).thenReturn("削皮");
        when(semanticEvaluator.commonChineseTerm(any())).thenAnswer(inv -> inv.getArgument(0));

        RuminationAiGenerator.ProbeDraft draft = new RuminationAiGenerator.ProbeDraft(
                "peel",
                "context_match",
                "Which kitchen preparation step removes the outer layer cleanly?",
                List.of("removing the skin carefully", "boiling the broth", "chilling in the fridge", "weighing flour"),
                "removing the skin carefully",
                "结合削皮动作判断"
        );
        when(aiGenerator.generateMaterialAndProbes(eq(userId), eq("生鲜采购与厨房料理"), any(), any(), any()))
                .thenReturn(new RuminationAiGenerator.GeneratedMaterialResult(
                        "Morning Prep in the Fresh Kitchen",
                        "自然串联生鲜料理核心词汇",
                        "Before starting the stew, the chef asked everyone to wash each potato, chop onion rings, and peel fresh carrots gently.",
                        "开始炖菜前，大厨请大家洗净土豆、切好洋葱并轻轻削去胡萝卜外皮。",
                        List.of(draft),
                        true
                ));

        LearningRuminationGraph existingGraph = new LearningRuminationGraph();
        existingGraph.setId(9001L);
        existingGraph.setBagTermsJson("[\"peel\",\"potato\",\"harvest\",\"apron\"]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(existingGraph);

        RuminationMaterialGenerateRequest req = new RuminationMaterialGenerateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setTargetWords(List.of("peel", "potato", "onion", "carrot", "boil"));

        RuminationMaterialResponse response = ruminationService.generateMaterial(userId, req);

        assertThat(response.getTitle()).isEqualTo("Morning Prep in the Fresh Kitchen");
        assertThat(response.getGenerationSource()).isEqualTo("ai");
        assertThat(response.getProbes()).hasSize(1);
        assertThat(response.getProbes().get(0).getCorrectAnswer()).isNull();
        verify(materialMapper).insert(any(LearningRuminationMaterial.class));
        verify(probeMapper).insert(any(LearningRuminationProbe.class));
        // 提交快照不包含的 harvest 与 apron 必须保留
        assertThat(existingGraph.getBagTermsJson()).isEqualTo("[\"harvest\",\"apron\"]");
    }

    @Test
    @DisplayName("AI协同: 节点4D衍生裂变绑定用户词本状态")
    void fissionNode_usesAiGeneratorAndAttachesEntryStatus() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(aiGenerator.generateFissionExpansions(eq(userId), eq("生鲜采购与厨房料理"), eq("potato"), any(), any()))
                .thenReturn(new RuminationAiGenerator.FissionResult(
                        List.of(new RuminationAiGenerator.DerivationDraft("peel", "削皮", "action", "potato")),
                        true
                ));
        when(wordbookService.findOwnedEntriesByTerms(userId, wordbookId, List.of("peel")))
                .thenReturn(List.of(entry));
        when(semanticEvaluator.resolveDefinition(entry, "削皮")).thenReturn("削皮/剥皮");

        RuminationFissionRequest request = new RuminationFissionRequest();
        request.setWordbookId(wordbookId);
        request.setScenarioCode("produce_kitchen");
        request.setParentNodeId("core-potato");
        request.setTargetWord("potato");

        RuminationFissionResponse response = ruminationService.fissionNode(userId, request);

        assertThat(response.getAiPowered()).isTrue();
        assertThat(response.getNodes()).hasSize(1);
        assertThat(response.getNodes().get(0).getEntryId()).isEqualTo(entry.getId());
        assertThat(response.getEdges()).hasSize(1);
        assertThat(response.getEdges().get(0).getSource()).isEqualTo("core-potato");
    }

    @Test
    @DisplayName("并发控制: 全量保存携带过期版本号时拒绝覆盖并抛出 409 版本冲突")
    void saveGraph_staleExpectedVersion_throwsConflictWithoutOverwriting() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        LearningRuminationGraph latest = new LearningRuminationGraph();
        latest.setId(7001L);
        latest.setUserId(userId);
        latest.setWordbookId(wordbookId);
        latest.setScenarioCode("produce_kitchen");
        latest.setVersion(2);
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(latest);

        RuminationGraphSaveRequest req = new RuminationGraphSaveRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setGraphNodesJson("[{\"id\":\"hub\",\"nodeType\":\"hub\"}]");
        req.setGraphEdgesJson("[]");
        req.setBagTermsJson("[]");
        req.setExpectedVersion(1);

        assertThatThrownBy(() -> ruminationService.saveGraph(userId, req))
                .isInstanceOf(LearningAssistantException.class)
                .satisfies(ex -> {
                    LearningAssistantException le = (LearningAssistantException) ex;
                    assertThat(le.getStatus().value()).isEqualTo(409);
                });
        verify(graphMapper, never()).updateById(any(LearningRuminationGraph.class));
    }

    @Test
    @DisplayName("并发控制: 增量更新节点坐标只改对应节点位置，保留并发裂变节点与词袋")
    void updateNodePositions_preservesConcurrentFissionNodesAndBag() throws Exception {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        LearningRuminationGraph existing = new LearningRuminationGraph();
        existing.setId(7002L);
        existing.setUserId(userId);
        existing.setWordbookId(wordbookId);
        existing.setScenarioCode("produce_kitchen");
        existing.setVersion(1);
        existing.setGraphNodesJson("[{\"id\":\"core-potato\",\"nodeType\":\"core\",\"term\":\"potato\",\"x\":10,\"y\":20},"
                + "{\"id\":\"exp-peel\",\"nodeType\":\"expansion\",\"term\":\"peel\",\"x\":80,\"y\":90}]");
        existing.setGraphEdgesJson("[{\"source\":\"core-potato\",\"target\":\"exp-peel\",\"relationType\":\"action\"}]");
        existing.setBagTermsJson("[\"potato\",\"peel\"]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(existing);
        when(graphMapper.updateById(any(LearningRuminationGraph.class))).thenReturn(1);

        RuminationGraphPositionsUpdateRequest.NodePositionRequest item = new RuminationGraphPositionsUpdateRequest.NodePositionRequest();
        item.setNodeId("core-potato");
        item.setX(188.0);
        item.setY(-96.0);
        item.setFixed(true);

        RuminationGraphPositionsUpdateRequest req = new RuminationGraphPositionsUpdateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setPositions(List.of(item));

        RuminationGraphSaveResponse response = ruminationService.updateNodePositions(userId, req);

        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getBagTerms()).containsExactly("potato", "peel");
        assertThat(existing.getGraphEdgesJson()).contains("exp-peel");
        var nodesTree = objectMapper.readTree(existing.getGraphNodesJson());
        assertThat(nodesTree).hasSize(2);
        assertThat(nodesTree.get(0).path("x").asInt()).isEqualTo(188);
        assertThat(nodesTree.get(0).path("y").asInt()).isEqualTo(-96);
        assertThat(nodesTree.get(0).path("fixed").asBoolean()).isTrue();
        assertThat(nodesTree.get(1).path("id").asText()).isEqualTo("exp-peel");
        assertThat(nodesTree.get(1).path("x").asInt()).isEqualTo(80);
    }

    @Test
    @DisplayName("并发控制: 增量加入和移除词袋词汇不触碰图谱节点与连线")
    void updateBagItem_addsAndRemovesTermWithoutOverwritingNodes() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        LearningRuminationGraph existing = new LearningRuminationGraph();
        existing.setId(7003L);
        existing.setUserId(userId);
        existing.setWordbookId(wordbookId);
        existing.setScenarioCode("produce_kitchen");
        existing.setVersion(3);
        existing.setGraphNodesJson("[{\"id\":\"exp-peel\",\"nodeType\":\"expansion\",\"term\":\"peel\"}]");
        existing.setGraphEdgesJson("[{\"source\":\"core-potato\",\"target\":\"exp-peel\",\"relationType\":\"action\"}]");
        existing.setBagTermsJson("[\"carrot\"]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(existing);
        when(graphMapper.updateById(any(LearningRuminationGraph.class))).thenReturn(1);

        RuminationGraphBagUpdateRequest addReq = new RuminationGraphBagUpdateRequest();
        addReq.setWordbookId(wordbookId);
        addReq.setScenarioCode("produce_kitchen");
        addReq.setTerm("exp-peel");
        addReq.setAdd(true);

        RuminationGraphSaveResponse afterAdd = ruminationService.updateBagItem(userId, addReq);
        assertThat(afterAdd.getVersion()).isEqualTo(4);
        assertThat(afterAdd.getBagTerms()).containsExactly("carrot", "peel");

        RuminationGraphBagUpdateRequest removeReq = new RuminationGraphBagUpdateRequest();
        removeReq.setWordbookId(wordbookId);
        removeReq.setScenarioCode("produce_kitchen");
        removeReq.setTerm("peel");
        removeReq.setAdd(false);

        RuminationGraphSaveResponse afterRemove = ruminationService.updateBagItem(userId, removeReq);
        assertThat(afterRemove.getVersion()).isEqualTo(5);
        assertThat(afterRemove.getBagTerms()).containsExactly("carrot");
        assertThat(existing.getGraphNodesJson()).contains("exp-peel");
    }

    @Test
    @DisplayName("题目来源: 混合 AI 出题与本地补题落库保留逐题 generationSource 并返回 graphVersion")
    void generateMaterial_persistsPerProbeGenerationSourceAndGraphVersion() {
        when(wordbookService.requireOwnedWordbook(userId, wordbookId)).thenReturn(wordbook);
        when(wordbookService.findOwnedEntriesByTerms(eq(userId), eq(wordbookId), any())).thenReturn(List.of(entry));
        when(semanticEvaluator.extractEntryDefinition(entry)).thenReturn("削皮");
        when(semanticEvaluator.commonChineseTerm(any())).thenReturn("词汇");

        List<RuminationAiGenerator.ProbeDraft> mixedProbes = List.of(
                new RuminationAiGenerator.ProbeDraft(
                        "peel", "context_match", "AI Q1",
                        List.of("peel skin", "chop wood", "boil water", "bake bread"), "peel skin", "h1", "ai"
                ),
                new RuminationAiGenerator.ProbeDraft(
                        "potato", "collocation", "Fallback Q2",
                        List.of("mashed potato", "fried ice", "cold spoon", "clean towel"), "mashed potato", "h2", "fallback"
                )
        );
        when(aiGenerator.generateMaterialAndProbes(eq(userId), eq("生鲜采购与厨房料理"), any(), any(), any()))
                .thenReturn(new RuminationAiGenerator.GeneratedMaterialResult(
                        "Kitchen Craft", "summary", "peel potato onion carrot boil", "中文", mixedProbes, true));
        LearningRuminationGraph existingGraph = new LearningRuminationGraph();
        existingGraph.setId(8801L);
        existingGraph.setVersion(2);
        existingGraph.setBagTermsJson("[\"peel\",\"potato\",\"onion\",\"carrot\",\"boil\",\"harvest\"]");
        when(graphMapper.selectByScenarioForUpdate(userId, wordbookId, "produce_kitchen")).thenReturn(existingGraph);

        RuminationMaterialGenerateRequest req = new RuminationMaterialGenerateRequest();
        req.setWordbookId(wordbookId);
        req.setScenarioCode("produce_kitchen");
        req.setTargetWords(List.of("peel", "potato", "onion", "carrot", "boil"));

        RuminationMaterialResponse response = ruminationService.generateMaterial(userId, req);

        assertThat(response.getGenerationSource()).isEqualTo("ai");
        assertThat(response.getGraphVersion()).isEqualTo(3);
        assertThat(response.getProbes()).hasSize(2);
        assertThat(response.getProbes().get(0).getGenerationSource()).isEqualTo("ai");
        assertThat(response.getProbes().get(1).getGenerationSource()).isEqualTo("fallback");

        ArgumentCaptor<LearningRuminationProbe> probeCaptor = ArgumentCaptor.forClass(LearningRuminationProbe.class);
        verify(probeMapper, org.mockito.Mockito.times(2)).insert(probeCaptor.capture());
        assertThat(probeCaptor.getAllValues().get(0).getGenerationSource()).isEqualTo("ai");
        assertThat(probeCaptor.getAllValues().get(1).getGenerationSource()).isEqualTo("fallback");
        assertThat(response.getBagConsumed()).isTrue();
    }

    @Test
    @DisplayName("重新生成详情: 当关联异步任务标记 consumeBag=false 时，材料详情返回 bagConsumed=false")
    void getMaterialDetail_regeneratedMaterialWithConsumeBagFalse_exposesBagConsumedFalse() {
        material.setTaskId(9105L);
        when(materialMapper.selectOne(any())).thenReturn(material);
        when(probeMapper.selectList(any())).thenReturn(List.of(probe));

        AiAsyncTask regenTask = new AiAsyncTask();
        regenTask.setId(9105L);
        regenTask.setPayloadJson("{\"consumeBag\":false,\"sourceMaterialId\":\"3001\"}");
        when(aiAsyncTaskService.require(userId, 9105L)).thenReturn(regenTask);

        RuminationMaterialResponse detail = ruminationService.getMaterialDetail(userId, materialId);

        assertThat(detail.getBagConsumed()).isFalse();
        assertThat(detail.getId()).isEqualTo(String.valueOf(materialId));
    }
}
