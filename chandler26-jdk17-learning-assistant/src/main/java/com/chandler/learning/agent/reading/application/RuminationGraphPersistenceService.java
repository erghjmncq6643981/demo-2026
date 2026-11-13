package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationGraphBagUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphPositionsUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphSaveRequest;
import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphSaveResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationScenarioResponse;
import com.chandler.learning.agent.reading.application.task.RuminationFissionCheckpoint;
import com.chandler.learning.agent.reading.application.task.RuminationScenarioCheckpoint;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 知识反刍星系图谱构建与幂等持久化服务。
 * <p>集中负责预置图谱装配、自定义场景星系落库、节点 4D 衍生裂变复核写回及反序列化共享。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationGraphPersistenceService {

    private final LearningRuminationGraphMapper graphMapper;
    private final RuminationSemanticEvaluator semanticEvaluator;
    private final ObjectMapper objectMapper;

    /** 根据节点生成单个核心或衍生节点响应对象。 */
    public RuminationNodeResponse buildCoreOrExpansionNode(String id, String nodeType, String term, String definition,
                                                           String dimension, LearningWordbookEntry entry) {
        RuminationNodeResponse node = new RuminationNodeResponse();
        node.setId(id);
        node.setNodeType(nodeType);
        node.setTerm(term);
        node.setDefinition(StringUtils.hasText(definition) ? definition : term);
        node.setDimension(dimension);
        node.setPhonetic(entry != null ? extractPhonetic(entry) : "");
        node.setEntryId(entry != null ? entry.getId() : null);
        node.setStatus(entry != null ? entry.getStatus() : "unlearned");
        node.setMasteryScore(entry != null ? entry.getMasteryScore() : 0);
        node.setReviewStage(entry != null ? entry.getReviewStage() : 0);
        return node;
    }

    /** 构建预置参考场景星系拓扑骨架（仅当数据库尚未初始化持久化时按需调用）。 */
    public RuminationGraphResponse buildPresetGraph(RuminationScenarioResponse meta,
                                                    List<String> candidateTerms,
                                                    List<LearningWordbookEntry> userEntries) {
        RuminationGraphResponse response = new RuminationGraphResponse();
        response.setScenarioCode(meta.getCode());
        response.setScenarioName(meta.getName());
        response.setScenarioIcon(meta.getIcon());

        List<RuminationNodeResponse> nodes = new ArrayList<>();
        List<RuminationEdgeResponse> edges = new ArrayList<>();

        RuminationNodeResponse hubNode = new RuminationNodeResponse();
        hubNode.setId("hub");
        hubNode.setNodeType("hub");
        hubNode.setTerm(meta.getName());
        hubNode.setLabel(meta.getIcon() + " " + meta.getName());
        hubNode.setSubLabel("场景核心");
        hubNode.setStatus("mastered");
        nodes.add(hubNode);

        Map<String, LearningWordbookEntry> entryMap = userEntries.stream()
                .collect(Collectors.toMap(e -> e.getNormalizedTerm().toLowerCase(Locale.ROOT), e -> e, (a, b) -> a));

        List<String> coreTerms = candidateTerms.stream()
                .filter(entryMap::containsKey)
                .limit(10)
                .toList();
        if (coreTerms.isEmpty()) {
            coreTerms = candidateTerms.stream().limit(8).toList();
        }

        for (String term : coreTerms) {
            LearningWordbookEntry entry = entryMap.get(term.toLowerCase(Locale.ROOT));
            RuminationNodeResponse node = buildCoreOrExpansionNode(
                    entry != null ? String.valueOf(entry.getId()) : "core-" + term,
                    "core",
                    term,
                    extractDefinition(entry, term),
                    null,
                    entry
            );
            nodes.add(node);

            RuminationEdgeResponse edge = new RuminationEdgeResponse();
            edge.setSource("hub");
            edge.setTarget(node.getId());
            edge.setRelationType("peer");
            edge.setLabel("");
            edges.add(edge);
        }

        semanticEvaluator.populatePreset4DDerivations(meta.getCode(), nodes, edges, entryMap);
        response.setNodes(nodes);
        response.setEdges(edges);
        response.setBagTerms(List.of());
        return response;
    }

    /** 将数据库持久化的图谱实体反序列化为前端拓扑响应。 */
    public RuminationGraphResponse deserializeSavedGraph(LearningRuminationGraph savedGraph,
                                                         String fallbackName,
                                                         String fallbackIcon) {
        RuminationGraphResponse response = new RuminationGraphResponse();
        response.setScenarioCode(savedGraph.getScenarioCode());
        response.setScenarioName(fallbackName);
        response.setScenarioIcon(fallbackIcon);
        response.setVersion(savedGraph.getVersion() == null ? 0 : savedGraph.getVersion());
        try {
            List<RuminationNodeResponse> savedNodes = objectMapper.readValue(savedGraph.getGraphNodesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationNodeResponse.class));
            List<RuminationEdgeResponse> savedEdges = objectMapper.readValue(savedGraph.getGraphEdgesJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationEdgeResponse.class));
            if (savedEdges != null) {
                for (RuminationEdgeResponse edge : savedEdges) {
                    if ("核心".equals(edge.getLabel())) {
                        edge.setLabel("");
                    }
                }
            }
            List<String> savedBag = StringUtils.hasText(savedGraph.getBagTermsJson())
                    ? objectMapper.readValue(savedGraph.getBagTermsJson(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
            response.setNodes(savedNodes);
            response.setEdges(savedEdges);
            response.setBagTerms(savedBag);
            if (savedNodes != null) {
                for (RuminationNodeResponse n : savedNodes) {
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
        } catch (Exception e) {
            log.warn("反序列化已保存星系拓扑失败", e);
        }
        return response;
    }

    /**
     * 在短事务内幂等落库自定义场景星系图谱；若同一场景编码已落库，直接复用现有记录。
     *
     * @param userId     所属用户 ID
     * @param wordbookId 所属单词本 ID
     * @param checkpoint 阶段 1 生成的场景星系检查点
     * @return 持久化后的图谱记录主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long persistCustomScenarioForTask(Long userId, Long wordbookId, RuminationScenarioCheckpoint checkpoint) {
        LearningRuminationGraph existing = graphMapper.selectByScenarioForUpdate(
                userId, wordbookId, checkpoint.scenarioCode());
        if (existing != null) {
            log.info("自定义场景落库命中幂等已存在记录 userId={} wordbookId={} scenario={} graphId={}",
                    userId, wordbookId, checkpoint.scenarioCode(), existing.getId());
            return existing.getId();
        }

        LearningRuminationGraph graph = new LearningRuminationGraph();
        graph.setUserId(userId);
        graph.setWordbookId(wordbookId);
        graph.setScenarioCode(checkpoint.scenarioCode());
        graph.setCreateBy(userId);
        graph.setUpdateBy(userId);
        graph.setGraphNodesJson(writeJson(checkpoint.nodes() == null ? List.of() : checkpoint.nodes()));
        graph.setGraphEdgesJson(writeJson(checkpoint.edges() == null ? List.of() : checkpoint.edges()));
        graph.setBagTermsJson("[]");
        graph.setVersion(0);

        try {
            graphMapper.insert(graph);
        } catch (DuplicateKeyException ex) {
            LearningRuminationGraph concurrent = graphMapper.selectByScenarioForUpdate(
                    userId, wordbookId, checkpoint.scenarioCode());
            if (concurrent != null) {
                log.info("自定义场景并发落库幂等复用 scenario={} graphId={}",
                        checkpoint.scenarioCode(), concurrent.getId());
                return concurrent.getId();
            }
            throw ex;
        }
        log.info("自定义场景异步任务落库完成 userId={} wordbookId={} scenario={} source={} graphId={}",
                userId, wordbookId, checkpoint.scenarioCode(), checkpoint.generationSource(), graph.getId());
        return graph.getId();
    }

    /**
     * 在短事务内锁定原场景星系图谱、重检父节点是否仍然存在，并将裂变节点与连线幂等写入原场景。
     *
     * @param userId               所属用户 ID
     * @param checkpoint           阶段 1 产出的候选 4D 裂变检查点
     * @param presetSeedSupplier   若原场景为未曾写库的预置场景，按需构建初始骨架
     * @return 带有应用状态与最终新增节点/连线的裂变结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationFissionResponse persistFissionForTask(
            Long userId,
            RuminationFissionCheckpoint checkpoint,
            Supplier<RuminationGraphResponse> presetSeedSupplier) {
        RuminationFissionResponse response = new RuminationFissionResponse();
        response.setWordbookId(String.valueOf(checkpoint.wordbookId()));
        response.setScenarioCode(checkpoint.scenarioCode());
        response.setParentNodeId(checkpoint.parentNodeId());
        response.setTargetWord(checkpoint.targetWord());
        response.setAiPowered(!"fallback".equalsIgnoreCase(checkpoint.generationSource()));

        LearningRuminationGraph graph = graphMapper.selectByScenarioForUpdate(
                userId, checkpoint.wordbookId(), checkpoint.scenarioCode());
        boolean newlyInitializedPreset = false;
        if (graph == null) {
            RuminationGraphResponse seed = presetSeedSupplier == null ? null : presetSeedSupplier.get();
            if (seed == null || seed.getNodes() == null) {
                response.setApplyStatus("skipped_graph_missing");
                response.setNodes(List.of());
                response.setEdges(List.of());
                return response;
            }
            graph = new LearningRuminationGraph();
            graph.setUserId(userId);
            graph.setWordbookId(checkpoint.wordbookId());
            graph.setScenarioCode(checkpoint.scenarioCode());
            graph.setCreateBy(userId);
            graph.setUpdateBy(userId);
            graph.setGraphNodesJson(writeJson(seed.getNodes()));
            graph.setGraphEdgesJson(writeJson(seed.getEdges() == null ? List.of() : seed.getEdges()));
            graph.setBagTermsJson("[]");
            newlyInitializedPreset = true;
        }

        List<RuminationNodeResponse> existingNodes = readNodeList(graph.getGraphNodesJson());
        List<RuminationEdgeResponse> existingEdges = readEdgeList(graph.getGraphEdgesJson());

        boolean parentExists = existingNodes.stream()
                .anyMatch(n -> checkpoint.parentNodeId().equals(n.getId()) && !"hub".equals(n.getNodeType()));
        if (!parentExists) {
            log.info("节点 4D 衍生裂变写回跳过：原场景「{}」中的父节点「{}」已不存在",
                    checkpoint.scenarioCode(), checkpoint.parentNodeId());
            response.setApplyStatus("skipped_parent_missing");
            response.setNodes(List.of());
            response.setEdges(List.of());
            return response;
        }

        Set<String> existingIds = existingNodes.stream()
                .map(RuminationNodeResponse::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> existingTerms = existingNodes.stream()
                .map(RuminationNodeResponse::getTerm)
                .filter(StringUtils::hasText)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RuminationNodeResponse> addedNodes = new ArrayList<>();
        List<RuminationEdgeResponse> addedEdges = new ArrayList<>();

        List<RuminationNodeResponse> candidates = checkpoint.candidateNodes() == null
                ? List.of() : checkpoint.candidateNodes();
        List<RuminationEdgeResponse> candidateEdges = checkpoint.candidateEdges() == null
                ? List.of() : checkpoint.candidateEdges();

        for (RuminationNodeResponse candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getTerm())) {
                continue;
            }
            String normTerm = candidate.getTerm().trim().toLowerCase(Locale.ROOT);
            if (existingIds.contains(candidate.getId()) || existingTerms.contains(normTerm)) {
                continue;
            }
            existingNodes.add(candidate);
            existingIds.add(candidate.getId());
            existingTerms.add(normTerm);
            addedNodes.add(candidate);

            RuminationEdgeResponse relEdge = candidateEdges.stream()
                    .filter(e -> candidate.getId().equals(e.getTarget()))
                    .findFirst()
                    .orElseGet(() -> {
                        RuminationEdgeResponse fallbackEdge = new RuminationEdgeResponse();
                        fallbackEdge.setSource(checkpoint.parentNodeId());
                        fallbackEdge.setTarget(candidate.getId());
                        fallbackEdge.setRelationType(StringUtils.hasText(candidate.getDimension())
                                ? candidate.getDimension() : "action");
                        fallbackEdge.setLabel("");
                        return fallbackEdge;
                    });
            relEdge.setSource(checkpoint.parentNodeId());
            existingEdges.add(relEdge);
            addedEdges.add(relEdge);
        }

        int previousVersion = graph.getVersion() == null ? 0 : graph.getVersion();
        Integer updatedVersion = previousVersion;
        if (newlyInitializedPreset || !addedNodes.isEmpty()) {
            graph.setGraphNodesJson(writeJson(existingNodes));
            graph.setGraphEdgesJson(writeJson(existingEdges));
            graph.setUpdateBy(userId);
            if (newlyInitializedPreset) {
                graph.setVersion(0);
                insertGraphOrThrowConflict(graph);
                updatedVersion = 0;
            } else {
                graphMapper.updateById(graph);
                updatedVersion = resolveUpdatedVersion(graph, previousVersion);
            }
        }

        response.setApplyStatus("applied");
        response.setGraphVersion(updatedVersion);
        response.setNodes(addedNodes);
        response.setEdges(addedEdges);
        log.info("节点 4D 衍生裂变落库完成 userId={} wordbookId={} scenario={} parent={} added={} source={} version={}",
                userId, checkpoint.wordbookId(), checkpoint.scenarioCode(), checkpoint.parentNodeId(),
                addedNodes.size(), checkpoint.generationSource(), updatedVersion);
        return response;
    }

    /**
     * 校验基线版本号并保存完整星系拓扑与词袋状态；若服务端存在更新版本或发生并发抢建，抛出 409 版本冲突拒绝静默覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationGraphSaveResponse saveGraphWithVersion(Long userId, Long wordbookId,
                                                              RuminationGraphSaveRequest request) {
        LearningRuminationGraph graph = graphMapper.selectByScenarioForUpdate(
                userId, wordbookId, request.getScenarioCode());
        if (graph == null) {
            if (request.getExpectedVersion() != null) {
                throw LearningAssistantException.of(LearningErrorCode.RUMINATION_GRAPH_VERSION_CONFLICT);
            }
            LearningRuminationGraph created = new LearningRuminationGraph();
            created.setUserId(userId);
            created.setWordbookId(wordbookId);
            created.setScenarioCode(request.getScenarioCode());
            created.setCreateBy(userId);
            created.setUpdateBy(userId);
            created.setGraphNodesJson(request.getGraphNodesJson());
            created.setGraphEdgesJson(request.getGraphEdgesJson());
            created.setBagTermsJson(request.getBagTermsJson());
            created.setVersion(0);
            insertGraphOrThrowConflict(created);
            log.info("用户「{}」首次落库场景「{}」星系图谱拓扑与词袋状态 version=0", userId, request.getScenarioCode());
            return buildSaveResponse(wordbookId, request.getScenarioCode(), 0,
                    readStringList(request.getBagTermsJson()));
        }

        int currentVersion = graph.getVersion() == null ? 0 : graph.getVersion();
        if (!Objects.equals(request.getExpectedVersion(), currentVersion)) {
            log.warn("知识反刍全量保存版本冲突拒绝覆盖 userId={} wordbookId={} scenario={} expectedVersion={} currentVersion={}",
                    userId, wordbookId, request.getScenarioCode(), request.getExpectedVersion(), currentVersion);
            throw LearningAssistantException.of(LearningErrorCode.RUMINATION_GRAPH_VERSION_CONFLICT);
        }

        graph.setGraphNodesJson(request.getGraphNodesJson());
        graph.setGraphEdgesJson(request.getGraphEdgesJson());
        graph.setBagTermsJson(request.getBagTermsJson());
        graph.setUpdateBy(userId);
        int nextVersion = updateGraphOrThrowConflict(graph, currentVersion);
        log.info("用户「{}」已持久化保存场景「{}」星系图谱拓扑与词袋状态 version={}",
                userId, request.getScenarioCode(), nextVersion);
        return buildSaveResponse(wordbookId, request.getScenarioCode(), nextVersion,
                readStringList(request.getBagTermsJson()));
    }

    /**
     * 增量更新部分星系节点固定坐标，保留并发衍生裂变写入的新节点、连线与词袋状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationGraphSaveResponse updateNodePositions(
            Long userId, Long wordbookId, RuminationGraphPositionsUpdateRequest request,
            Supplier<RuminationGraphResponse> presetSeedSupplier) {
        SeededOrExistingGraph target = loadOrSeedGraphForUpdate(
                userId, wordbookId, request.getScenarioCode(), presetSeedSupplier);
        LearningRuminationGraph graph = target.graph();
        List<RuminationNodeResponse> nodes = readNodeList(graph.getGraphNodesJson());

        Map<String, RuminationGraphPositionsUpdateRequest.NodePositionRequest> updatesById = new LinkedHashMap<>();
        for (RuminationGraphPositionsUpdateRequest.NodePositionRequest item : request.getPositions()) {
            if (item != null && StringUtils.hasText(item.getNodeId())) {
                updatesById.put(item.getNodeId(), item);
            }
        }
        boolean changed = false;
        for (RuminationNodeResponse node : nodes) {
            RuminationGraphPositionsUpdateRequest.NodePositionRequest item = updatesById.get(node.getId());
            if (item != null) {
                node.setX(item.getX());
                node.setY(item.getY());
                node.setFixed(item.getFixed() == null || Boolean.TRUE.equals(item.getFixed()));
                changed = true;
            }
        }
        if (changed) {
            graph.setGraphNodesJson(writeJson(nodes));
        }
        graph.setUpdateBy(userId);
        int nextVersion = persistSeededOrUpdatedGraph(graph, target.newlyCreated(), target.previousVersion());
        return buildSaveResponse(wordbookId, request.getScenarioCode(), nextVersion,
                readStringList(graph.getBagTermsJson()));
    }

    /**
     * 增量加入或移除单个词袋词条，保留并发衍生裂变与坐标布局，避免全图快照互相覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuminationGraphSaveResponse updateBagItem(
            Long userId, Long wordbookId, RuminationGraphBagUpdateRequest request,
            Supplier<RuminationGraphResponse> presetSeedSupplier) {
        SeededOrExistingGraph target = loadOrSeedGraphForUpdate(
                userId, wordbookId, request.getScenarioCode(), presetSeedSupplier);
        LearningRuminationGraph graph = target.graph();
        List<RuminationNodeResponse> nodes = readNodeList(graph.getGraphNodesJson());
        List<String> bagTerms = new ArrayList<>(readStringList(graph.getBagTermsJson()));

        String rawKey = request.getTerm().trim();
        String canonicalTerm = resolveCanonicalTerm(nodes, rawKey);
        if (Boolean.TRUE.equals(request.getAdd())) {
            boolean alreadyExists = bagTerms.stream()
                    .anyMatch(t -> t.equalsIgnoreCase(canonicalTerm) || t.equalsIgnoreCase(rawKey));
            if (!alreadyExists) {
                bagTerms.add(canonicalTerm);
            }
        } else {
            bagTerms.removeIf(t -> t.equalsIgnoreCase(canonicalTerm) || t.equalsIgnoreCase(rawKey));
        }

        graph.setBagTermsJson(writeJson(bagTerms));
        graph.setUpdateBy(userId);
        int nextVersion = persistSeededOrUpdatedGraph(graph, target.newlyCreated(), target.previousVersion());
        return buildSaveResponse(wordbookId, request.getScenarioCode(), nextVersion, bagTerms);
    }

    private record SeededOrExistingGraph(LearningRuminationGraph graph, boolean newlyCreated, int previousVersion) {}

    private SeededOrExistingGraph loadOrSeedGraphForUpdate(
            Long userId, Long wordbookId, String scenarioCode,
            Supplier<RuminationGraphResponse> presetSeedSupplier) {
        LearningRuminationGraph existing = graphMapper.selectByScenarioForUpdate(userId, wordbookId, scenarioCode);
        if (existing != null) {
            int prev = existing.getVersion() == null ? 0 : existing.getVersion();
            return new SeededOrExistingGraph(existing, false, prev);
        }
        RuminationGraphResponse seed = presetSeedSupplier == null ? null : presetSeedSupplier.get();
        if (seed == null || seed.getNodes() == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "反刍场景不存在或尚未初始化");
        }
        LearningRuminationGraph created = new LearningRuminationGraph();
        created.setUserId(userId);
        created.setWordbookId(wordbookId);
        created.setScenarioCode(scenarioCode);
        created.setCreateBy(userId);
        created.setUpdateBy(userId);
        created.setGraphNodesJson(writeJson(seed.getNodes()));
        created.setGraphEdgesJson(writeJson(seed.getEdges() == null ? List.of() : seed.getEdges()));
        created.setBagTermsJson(writeJson(seed.getBagTerms() == null ? List.of() : seed.getBagTerms()));
        created.setVersion(0);
        return new SeededOrExistingGraph(created, true, 0);
    }

    private int persistSeededOrUpdatedGraph(LearningRuminationGraph graph, boolean newlyCreated, int previousVersion) {
        if (newlyCreated) {
            insertGraphOrThrowConflict(graph);
            return 0;
        }
        return updateGraphOrThrowConflict(graph, previousVersion);
    }

    private void insertGraphOrThrowConflict(LearningRuminationGraph graph) {
        try {
            graphMapper.insert(graph);
        } catch (DuplicateKeyException ex) {
            throw LearningAssistantException.of(LearningErrorCode.RUMINATION_GRAPH_VERSION_CONFLICT);
        }
    }

    private int updateGraphOrThrowConflict(LearningRuminationGraph graph, int previousVersion) {
        graphMapper.updateById(graph);
        return resolveUpdatedVersion(graph, previousVersion);
    }

    private int resolveUpdatedVersion(LearningRuminationGraph graph, int previousVersion) {
        int expectedNextVersion = previousVersion + 1;
        if (graph.getVersion() == null || graph.getVersion() < expectedNextVersion) {
            graph.setVersion(expectedNextVersion);
        }
        return graph.getVersion();
    }

    private String resolveCanonicalTerm(List<RuminationNodeResponse> nodes, String rawKey) {
        for (RuminationNodeResponse node : nodes) {
            if (node == null) {
                continue;
            }
            if (rawKey.equals(node.getId())
                    || (StringUtils.hasText(node.getTerm()) && rawKey.equalsIgnoreCase(node.getTerm()))) {
                return StringUtils.hasText(node.getTerm()) ? node.getTerm().trim() : rawKey;
            }
        }
        return rawKey;
    }

    private RuminationGraphSaveResponse buildSaveResponse(
            Long wordbookId, String scenarioCode, Integer version, List<String> bagTerms) {
        RuminationGraphSaveResponse response = new RuminationGraphSaveResponse();
        response.setWordbookId(String.valueOf(wordbookId));
        response.setScenarioCode(scenarioCode);
        response.setVersion(version);
        response.setBagTerms(bagTerms == null ? List.of() : bagTerms);
        return response;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** 提取词汇中文释义，与语义评估器保持一致。 */
    public String extractDefinition(LearningWordbookEntry entry, String fallbackTerm) {
        return semanticEvaluator.resolveDefinition(entry, fallbackTerm);
    }

    /** 提取词汇音标文本。 */
    public String extractPhonetic(LearningWordbookEntry entry) {
        return semanticEvaluator.extractEntryPhonetic(entry);
    }

    private List<RuminationNodeResponse> readNodeList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationNodeResponse.class)));
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private List<RuminationEdgeResponse> readEdgeList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RuminationEdgeResponse.class)));
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
