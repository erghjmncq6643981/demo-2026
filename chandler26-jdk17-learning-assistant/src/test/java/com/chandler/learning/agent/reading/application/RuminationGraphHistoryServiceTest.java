package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.request.RuminationGraphHistoryRestoreRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphSaveRequest;
import com.chandler.learning.agent.reading.api.response.RuminationGraphHistoryItemResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphSaveResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraphHistory;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphHistoryMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识反刍场景图谱历史快照与 30 次滚动淘汰单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RuminationGraphHistoryServiceTest {

    @Mock
    private LearningRuminationGraphHistoryMapper historyMapper;

    @Mock
    private LearningRuminationGraphMapper graphMapper;

    @Mock
    private WordbookService wordbookService;

    @Mock
    private RuminationSemanticEvaluator semanticEvaluator;

    private ObjectMapper objectMapper;
    private RuminationGraphHistoryService historyService;
    private RuminationGraphPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        historyService = new RuminationGraphHistoryService(
                historyMapper, graphMapper, wordbookService, objectMapper);
        persistenceService = new RuminationGraphPersistenceService(
                graphMapper, historyService, semanticEvaluator, objectMapper);

        LearningWordbook wb = new LearningWordbook();
        wb.setId(200L);
        wb.setUserId(100L);
        lenient().when(wordbookService.requireOwnedWordbook(100L, 200L)).thenReturn(wb);
    }

    @Test
    void recordSnapshotAndTrim_keepsAtMost30RecordsPerScenario() {
        LearningRuminationGraph graph = new LearningRuminationGraph();
        graph.setId(9001L);
        graph.setUserId(100L);
        graph.setWordbookId(200L);
        graph.setScenarioCode("produce_kitchen");
        graph.setVersion(31);
        graph.setUpdateBy(100L);
        graph.setGraphNodesJson("[{\"id\":\"hub\",\"nodeType\":\"hub\",\"term\":\"果蔬\"},{\"id\":\"n1\",\"term\":\"ginger\"}]");
        graph.setGraphEdgesJson("[{\"source\":\"hub\",\"target\":\"n1\"}]");
        graph.setBagTermsJson("[\"ginger\"]");

        when(historyMapper.selectOverflowHistoryIds(
                100L, 200L, "produce_kitchen", RuminationGraphHistoryService.MAX_HISTORY_PER_SCENARIO))
                .thenReturn(List.of(1001L, 1002L));

        historyService.recordSnapshotAndTrim(graph, "新增生姜节点");

        ArgumentCaptor<LearningRuminationGraphHistory> captor =
                ArgumentCaptor.forClass(LearningRuminationGraphHistory.class);
        verify(historyMapper).insert(captor.capture());

        LearningRuminationGraphHistory inserted = captor.getValue();
        assertThat(inserted.getGraphId()).isEqualTo(9001L);
        assertThat(inserted.getGraphVersion()).isEqualTo(31);
        assertThat(inserted.getNodeCount()).isEqualTo(2);
        assertThat(inserted.getEdgeCount()).isEqualTo(1);
        assertThat(inserted.getChangeSummary()).isEqualTo("新增生姜节点");

        verify(historyMapper).softDeleteByIds(eq(List.of(1001L, 1002L)), eq(100L));
    }

    @Test
    void saveGraphWithVersion_archivesExistingBaselineBeforeOverwriteAndRecordsNewVersion() {
        LearningRuminationGraph existing = new LearningRuminationGraph();
        existing.setId(9001L);
        existing.setUserId(100L);
        existing.setWordbookId(200L);
        existing.setScenarioCode("produce_kitchen");
        existing.setVersion(10);
        existing.setGraphNodesJson("[{\"id\":\"hub\",\"term\":\"果蔬\"},{\"id\":\"cucumber\",\"term\":\"cucumber\"}]");
        existing.setGraphEdgesJson("[{\"source\":\"hub\",\"target\":\"cucumber\"}]");
        existing.setBagTermsJson("[]");

        when(graphMapper.selectByScenarioForUpdate(100L, 200L, "produce_kitchen")).thenReturn(existing);
        when(historyMapper.countByScenarioAndVersion(100L, 200L, "produce_kitchen", 10)).thenReturn(0L);

        RuminationGraphSaveRequest saveReq = new RuminationGraphSaveRequest();
        saveReq.setWordbookId(200L);
        saveReq.setScenarioCode("produce_kitchen");
        saveReq.setExpectedVersion(10);
        saveReq.setChangeSummary("增量添加 blueberry、ginger、scallion");
        saveReq.setGraphNodesJson("[{\"id\":\"hub\",\"term\":\"果蔬\"},{\"id\":\"cucumber\",\"term\":\"cucumber\"},{\"id\":\"ginger\",\"term\":\"ginger\"}]");
        saveReq.setGraphEdgesJson("[{\"source\":\"hub\",\"target\":\"cucumber\"},{\"source\":\"hub\",\"target\":\"ginger\"}]");
        saveReq.setBagTermsJson("[]");

        RuminationGraphSaveResponse res = persistenceService.saveGraphWithVersion(100L, 200L, saveReq);

        assertThat(res.getVersion()).isEqualTo(11);

        ArgumentCaptor<LearningRuminationGraphHistory> captor =
                ArgumentCaptor.forClass(LearningRuminationGraphHistory.class);
        verify(historyMapper, times(2)).insert(captor.capture());

        List<LearningRuminationGraphHistory> histories = captor.getAllValues();
        assertThat(histories.get(0).getGraphVersion()).isEqualTo(10);
        assertThat(histories.get(0).getNodeCount()).isEqualTo(2);
        assertThat(histories.get(0).getChangeSummary()).contains("存量基线自动归档 (v10)");

        assertThat(histories.get(1).getGraphVersion()).isEqualTo(11);
        assertThat(histories.get(1).getNodeCount()).isEqualTo(3);
        assertThat(histories.get(1).getChangeSummary()).isEqualTo("增量添加 blueberry、ginger、scallion");
    }

    @Test
    void listRecentHistory_returnsCompactSummariesUpTo30() {
        LearningRuminationGraphHistory h1 = new LearningRuminationGraphHistory();
        h1.setId(5002L);
        h1.setGraphId(9001L);
        h1.setUserId(100L);
        h1.setWordbookId(200L);
        h1.setScenarioCode("produce_kitchen");
        h1.setGraphVersion(398);
        h1.setNodeCount(41);
        h1.setEdgeCount(40);
        h1.setChangeSummary("恢复黄瓜与玉米节点");
        h1.setCreateTime(LocalDateTime.now());

        when(historyMapper.selectRecentSummaries(100L, 200L, "produce_kitchen", 30))
                .thenReturn(List.of(h1));

        List<RuminationGraphHistoryItemResponse> list =
                historyService.listRecentHistory(100L, 200L, "produce_kitchen");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo("5002");
        assertThat(list.get(0).getGraphVersion()).isEqualTo(398);
        assertThat(list.get(0).getNodeCount()).isEqualTo(41);
        assertThat(list.get(0).getChangeSummary()).isEqualTo("恢复黄瓜与玉米节点");
    }

    @Test
    void restoreFromHistory_restoresSnapshotAndCreatesNewVersionEntry() {
        LearningRuminationGraphHistory targetSnapshot = new LearningRuminationGraphHistory();
        targetSnapshot.setId(5001L);
        targetSnapshot.setGraphId(9001L);
        targetSnapshot.setUserId(100L);
        targetSnapshot.setWordbookId(200L);
        targetSnapshot.setScenarioCode("produce_kitchen");
        targetSnapshot.setGraphVersion(15);
        targetSnapshot.setDeleted(false);
        targetSnapshot.setGraphNodesJson("[{\"id\":\"hub\",\"nodeType\":\"hub\",\"term\":\"生鲜采购\",\"label\":\"🥦 果蔬\"},{\"id\":\"blueberry\",\"nodeType\":\"expansion\",\"term\":\"blueberry\",\"definition\":\"蓝莓\"}]");
        targetSnapshot.setGraphEdgesJson("[{\"source\":\"hub\",\"target\":\"blueberry\",\"relationType\":\"peer\"}]");
        targetSnapshot.setBagTermsJson("[\"blueberry\"]");

        LearningRuminationGraph currentGraph = new LearningRuminationGraph();
        currentGraph.setId(9001L);
        currentGraph.setUserId(100L);
        currentGraph.setWordbookId(200L);
        currentGraph.setScenarioCode("produce_kitchen");
        currentGraph.setVersion(20);
        currentGraph.setGraphNodesJson("[{\"id\":\"hub\",\"nodeType\":\"hub\",\"term\":\"生鲜采购\"}]");
        currentGraph.setGraphEdgesJson("[]");
        currentGraph.setBagTermsJson("[]");

        when(historyMapper.selectById(5001L)).thenReturn(targetSnapshot);
        when(graphMapper.selectByScenarioForUpdate(100L, 200L, "produce_kitchen")).thenReturn(currentGraph);
        when(historyMapper.countByScenarioAndVersion(100L, 200L, "produce_kitchen", 20)).thenReturn(1L);

        RuminationGraphHistoryRestoreRequest req = new RuminationGraphHistoryRestoreRequest();
        req.setWordbookId(200L);
        req.setScenarioCode("produce_kitchen");
        req.setHistoryId(5001L);

        RuminationGraphResponse restored = historyService.restoreFromHistory(100L, req);

        assertThat(restored.getVersion()).isEqualTo(21);
        assertThat(restored.getNodes()).hasSize(2);
        assertThat(restored.getNodes().get(1).getTerm()).isEqualTo("blueberry");
        assertThat(restored.getBagTerms()).containsExactly("blueberry");

        ArgumentCaptor<LearningRuminationGraphHistory> captor =
                ArgumentCaptor.forClass(LearningRuminationGraphHistory.class);
        verify(historyMapper).insert(captor.capture());
        assertThat(captor.getValue().getGraphVersion()).isEqualTo(21);
        assertThat(captor.getValue().getChangeSummary()).isEqualTo("从历史版本 v15 恢复");
    }
}
