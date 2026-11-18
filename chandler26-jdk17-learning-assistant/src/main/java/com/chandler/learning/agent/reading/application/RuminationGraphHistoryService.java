package com.chandler.learning.agent.reading.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationGraphHistoryRestoreRequest;
import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphHistoryItemResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraphHistory;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphHistoryMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 知识反刍场景星系图谱历史快照版本管理服务。
 * <p>负责图谱每次变更时的历史快照归档、每个场景最近 30 次记录的滚动淘汰保护，以及按指定历史版本安全恢复图谱。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationGraphHistoryService {

    /** 每个场景保留的最大历史变更记录条数。 */
    public static final int MAX_HISTORY_PER_SCENARIO = 30;

    private static final int MAX_SUMMARY_LENGTH = 250;

    private final LearningRuminationGraphHistoryMapper historyMapper;
    private final LearningRuminationGraphMapper graphMapper;
    private final WordbookService wordbookService;
    private final ObjectMapper objectMapper;

    /**
     * 在更新现有图谱前，确保更新前的当前版本已在历史表中归档（防止历史表上线前的存量基线在首次覆写时丢失）。
     *
     * @param existingGraph 即将被修改的当前图谱持久化实体
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureBaselineArchived(LearningRuminationGraph existingGraph) {
        if (existingGraph == null || existingGraph.getId() == null) {
            return;
        }
        int currentVersion = existingGraph.getVersion() == null ? 0 : existingGraph.getVersion();
        long count = historyMapper.countByScenarioAndVersion(
                existingGraph.getUserId(),
                existingGraph.getWordbookId(),
                existingGraph.getScenarioCode(),
                currentVersion
        );
        if (count == 0L) {
            recordSnapshotAndTrim(existingGraph, "存量基线自动归档 (v" + currentVersion + ")");
        }
    }

    /**
     * 记录图谱当前快照并执行每个场景最多保留最近 30 条记录的滚动软删除淘汰。
     *
     * @param graph         已保存的主图谱实体
     * @param changeSummary 本次变更原因摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordSnapshotAndTrim(LearningRuminationGraph graph, String changeSummary) {
        if (graph == null || graph.getId() == null) {
            return;
        }
        String nodesJson = StringUtils.hasText(graph.getGraphNodesJson()) ? graph.getGraphNodesJson() : "[]";
        String edgesJson = StringUtils.hasText(graph.getGraphEdgesJson()) ? graph.getGraphEdgesJson() : "[]";
        String bagJson = StringUtils.hasText(graph.getBagTermsJson()) ? graph.getBagTermsJson() : "[]";

        int nodeCount = countJsonArrayElements(nodesJson);
        int edgeCount = countJsonArrayElements(edgesJson);
        int graphVersion = graph.getVersion() == null ? 0 : graph.getVersion();
        Long operatorId = graph.getUpdateBy() != null ? graph.getUpdateBy() : graph.getUserId();

        LearningRuminationGraphHistory history = new LearningRuminationGraphHistory();
        history.setGraphId(graph.getId());
        history.setUserId(graph.getUserId());
        history.setWordbookId(graph.getWordbookId());
        history.setScenarioCode(graph.getScenarioCode());
        history.setGraphVersion(graphVersion);
        history.setNodeCount(nodeCount);
        history.setEdgeCount(edgeCount);
        history.setGraphNodesJson(nodesJson);
        history.setGraphEdgesJson(edgesJson);
        history.setBagTermsJson(bagJson);
        history.setChangeSummary(normalizeSummary(changeSummary, graphVersion));
        history.setCreateBy(operatorId);
        history.setUpdateBy(operatorId);

        historyMapper.insert(history);

        List<Long> overflowIds = historyMapper.selectOverflowHistoryIds(
                graph.getUserId(), graph.getWordbookId(), graph.getScenarioCode(), MAX_HISTORY_PER_SCENARIO);
        if (overflowIds != null && !overflowIds.isEmpty()) {
            int trimmed = historyMapper.softDeleteByIds(overflowIds, operatorId);
            log.info("知识反刍场景图谱历史版本滚动淘汰完成: userId={} scenario={} 保留上限={} 本次淘汰条数={}",
                    graph.getUserId(), graph.getScenarioCode(), MAX_HISTORY_PER_SCENARIO, trimmed);
        }
    }

    /**
     * 查询指定场景最近 30 次的历史版本变更摘要列表。
     *
     * @param userId       当前用户 ID
     * @param wordbookId   个人单词本 ID
     * @param scenarioCode 主题场景编码
     * @return 按版本号倒序排列的历史版本摘要列表
     */
    public List<RuminationGraphHistoryItemResponse> listRecentHistory(Long userId, Long wordbookId, String scenarioCode) {
        wordbookService.requireOwnedWordbook(userId, wordbookId);
        List<LearningRuminationGraphHistory> records = historyMapper.selectRecentSummaries(
                userId, wordbookId, scenarioCode, MAX_HISTORY_PER_SCENARIO);
        return records.stream().map(this::toItemResponse).toList();
    }

    /**
     * 将指定场景图谱恢复到目标历史快照版本，并生成一条新的版本与历史记录。
     *
     * @param userId  当前用户 ID
     * @param request 历史版本恢复请求
     * @return 恢复后的完整图谱响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationGraphResponse restoreFromHistory(Long userId, RuminationGraphHistoryRestoreRequest request) {
        wordbookService.requireOwnedWordbook(userId, request.getWordbookId());

        LearningRuminationGraphHistory targetHistory = historyMapper.selectById(request.getHistoryId());
        if (targetHistory == null
                || Boolean.TRUE.equals(targetHistory.getDeleted())
                || !Objects.equals(targetHistory.getUserId(), userId)
                || !Objects.equals(targetHistory.getWordbookId(), request.getWordbookId())
                || !Objects.equals(targetHistory.getScenarioCode(), request.getScenarioCode())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "目标历史版本快照不存在或已被清理");
        }

        LearningRuminationGraph graph = graphMapper.selectByScenarioForUpdate(
                userId, request.getWordbookId(), request.getScenarioCode());
        int nextVersion;
        if (graph == null) {
            graph = new LearningRuminationGraph();
            graph.setUserId(userId);
            graph.setWordbookId(request.getWordbookId());
            graph.setScenarioCode(request.getScenarioCode());
            graph.setCreateBy(userId);
            graph.setUpdateBy(userId);
            graph.setGraphNodesJson(targetHistory.getGraphNodesJson());
            graph.setGraphEdgesJson(targetHistory.getGraphEdgesJson());
            graph.setBagTermsJson(targetHistory.getBagTermsJson());
            graph.setVersion(0);
            graphMapper.insert(graph);
            nextVersion = 0;
        } else {
            ensureBaselineArchived(graph);
            int previousVersion = graph.getVersion() == null ? 0 : graph.getVersion();
            graph.setGraphNodesJson(targetHistory.getGraphNodesJson());
            graph.setGraphEdgesJson(targetHistory.getGraphEdgesJson());
            graph.setBagTermsJson(targetHistory.getBagTermsJson());
            graph.setUpdateBy(userId);
            graphMapper.updateById(graph);
            nextVersion = previousVersion + 1;
            if (graph.getVersion() == null || graph.getVersion() < nextVersion) {
                graph.setVersion(nextVersion);
            }
        }

        String summary = "从历史版本 v" + targetHistory.getGraphVersion() + " 恢复";
        recordSnapshotAndTrim(graph, summary);
        log.info("用户「{}」已将场景「{}」从历史快照 id={} (v{}) 恢复至新版本 v{}",
                userId, request.getScenarioCode(), targetHistory.getId(), targetHistory.getGraphVersion(), nextVersion);

        return buildRestoredGraphResponse(graph);
    }

    private RuminationGraphHistoryItemResponse toItemResponse(LearningRuminationGraphHistory entity) {
        RuminationGraphHistoryItemResponse item = new RuminationGraphHistoryItemResponse();
        item.setId(String.valueOf(entity.getId()));
        item.setGraphId(String.valueOf(entity.getGraphId()));
        item.setWordbookId(String.valueOf(entity.getWordbookId()));
        item.setScenarioCode(entity.getScenarioCode());
        item.setGraphVersion(entity.getGraphVersion());
        item.setNodeCount(entity.getNodeCount());
        item.setEdgeCount(entity.getEdgeCount());
        item.setChangeSummary(entity.getChangeSummary());
        item.setCreateTime(entity.getCreateTime());
        return item;
    }

    private RuminationGraphResponse buildRestoredGraphResponse(LearningRuminationGraph graph) {
        RuminationGraphResponse response = new RuminationGraphResponse();
        response.setScenarioCode(graph.getScenarioCode());
        response.setVersion(graph.getVersion());
        response.setBuilding(false);
        try {
            List<RuminationNodeResponse> nodes = objectMapper.readValue(graph.getGraphNodesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationNodeResponse.class));
            List<RuminationEdgeResponse> edges = objectMapper.readValue(graph.getGraphEdgesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationEdgeResponse.class));
            List<String> bag = StringUtils.hasText(graph.getBagTermsJson())
                    ? objectMapper.readValue(graph.getBagTermsJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
            response.setNodes(nodes);
            response.setEdges(edges);
            response.setBagTerms(bag);
            if (nodes != null) {
                for (RuminationNodeResponse n : nodes) {
                    if ("hub".equals(n.getNodeType()) && StringUtils.hasText(n.getTerm())) {
                        response.setScenarioName(n.getTerm());
                        if (StringUtils.hasText(n.getLabel())) {
                            String[] parts = n.getLabel().trim().split("\\s+", 2);
                            if (parts.length > 1) {
                                response.setScenarioIcon(parts[0]);
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("反序列化恢复后的图谱数据异常", ex);
        }
        return response;
    }

    private int countJsonArrayElements(String json) {
        if (!StringUtils.hasText(json)) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isArray() ? node.size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private String normalizeSummary(String summary, int version) {
        String text = StringUtils.hasText(summary) ? summary.trim() : "保存图谱变更 (v" + version + ")";
        return text.length() > MAX_SUMMARY_LENGTH ? text.substring(0, MAX_SUMMARY_LENGTH) : text;
    }
}
