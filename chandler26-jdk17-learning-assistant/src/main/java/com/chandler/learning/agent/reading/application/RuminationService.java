package com.chandler.learning.agent.reading.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.reading.api.request.RuminationCustomScenarioRequest;
import com.chandler.learning.agent.reading.api.request.RuminationFissionRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphBagUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphPositionsUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphSaveRequest;
import com.chandler.learning.agent.reading.api.request.RuminationMaterialGenerateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationProbeSubmitRequest;
import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphSaveResponse;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeSubmitResponse;
import com.chandler.learning.agent.reading.api.response.RuminationScenarioResponse;
import com.chandler.learning.agent.reading.application.task.RuminationFissionCheckpoint;
import com.chandler.learning.agent.reading.application.task.RuminationScenarioCheckpoint;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationGraph;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationGraphMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationProbeMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.vocabulary.api.request.AddWordbookEntryRequest;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.api.request.ReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识反刍应用服务。
 * <p>负责场景图谱拓扑构建、默认 AI 模型协同生成、真实考纲词汇关联、4D衍生连线、场景材料文章与微环境探针持久化结算。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationService {

    private final WordbookService wordbookService;
    private final ObjectMapper objectMapper;
    private final LearningRuminationGraphMapper graphMapper;
    private final LearningRuminationMaterialMapper materialMapper;
    private final LearningRuminationProbeMapper probeMapper;
    private final RuminationSemanticEvaluator semanticEvaluator;
    private final RuminationAiGenerator aiGenerator;
    private final RuminationMaterialPersistenceService persistenceService;
    private final RuminationGraphPersistenceService graphPersistenceService;
    private final AiAsyncTaskService aiAsyncTaskService;

    /** 自定义场景中直连 Hub 的核心考纲词最大推荐数量上限，与官方预置场景保持同等视觉疏密。 */
    public static final int MAX_CUSTOM_SCENARIO_CORE_WORDS = 18;

    /** 预置场景及其关联的考纲核心词汇集合。只保留一个预置场景，方便用户参考。 */
    private static final Map<String, List<String>> SCENARIO_TERMS = Map.of(
            "produce_kitchen", List.of(
                    "apple", "banana", "potato", "tomato", "cabbage", "onion", "carrot", "mushroom",
                    "grape", "corn", "pea", "bean", "lemon", "orange", "peach", "pear", "pineapple",
                    "strawberry", "watermelon", "melon", "sugar cane", "pepper", "eggplant", "garlic",
                    "cucumber", "spinach", "lettuce", "celery", "broccoli", "fruit", "vegetable"
            )
    );

    /** 预置场景元数据清单。只保留一个预置场景，方便用户参考。 */
    private static final List<RuminationScenarioResponse> SCENARIOS_METADATA = List.of(
            createMeta("produce_kitchen", "生鲜采购与厨房料理", "🥦", "涵盖常见果蔬生鲜、烹饪刀工料理动词、加工下游产物及口感感官词汇。")
    );

    private static RuminationScenarioResponse createMeta(String code, String name, String icon, String desc) {
        RuminationScenarioResponse meta = new RuminationScenarioResponse();
        meta.setCode(code);
        meta.setName(name);
        meta.setIcon(icon);
        meta.setDescription(desc);
        return meta;
    }

    /** 查询用户指定词本在全部主题场景下的覆盖与待反刍统计。 */
    public List<RuminationScenarioResponse> listScenarios(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, wordbookId);
        LocalDateTime now = LocalDateTime.now();
        List<RuminationScenarioResponse> responses = new ArrayList<>();

        for (RuminationScenarioResponse meta : SCENARIOS_METADATA) {
            RuminationScenarioResponse item = new RuminationScenarioResponse();
            item.setCode(meta.getCode());
            item.setName(meta.getName());
            item.setIcon(meta.getIcon());
            item.setDescription(meta.getDescription());

            List<String> terms = SCENARIO_TERMS.getOrDefault(meta.getCode(), List.of());
            Map<String, Integer> counts = wordbookService.countMatchingTerms(userId, wordbook.getId(), terms, now);
            item.setTotalWords(counts.getOrDefault("total", 0));
            item.setDueWords(counts.getOrDefault("due", 0));
            responses.add(item);
        }

        // 查询用户自主拓展创建并持久化的自定义场景星系
        List<LearningRuminationGraph> customGraphs = graphMapper.selectList(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .select(LearningRuminationGraph::getScenarioCode, LearningRuminationGraph::getGraphNodesJson)
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbook.getId())
                        .eq(LearningRuminationGraph::getDeleted, 0)
        );
        for (LearningRuminationGraph cg : customGraphs) {
            if (SCENARIOS_METADATA.stream().noneMatch(s -> s.getCode().equals(cg.getScenarioCode()))) {
                RuminationScenarioResponse item = new RuminationScenarioResponse();
                item.setCode(cg.getScenarioCode());
                String scName = "自定义场景";
                String scIcon = "🏷️";
                if (StringUtils.hasText(cg.getGraphNodesJson())) {
                    try {
                        JsonNode nodes = objectMapper.readTree(cg.getGraphNodesJson());
                        for (JsonNode n : nodes) {
                            if ("hub".equals(n.path("nodeType").asText())) {
                                String label = n.path("label").asText();
                                if (StringUtils.hasText(label)) {
                                    scName = label.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "").trim();
                                    String[] parts = label.trim().split("\\s+", 2);
                                    if (parts.length > 1) {
                                        scIcon = parts[0];
                                    }
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                item.setName(scName);
                item.setIcon(scIcon);
                item.setDescription("用户自主创建的自定义语境知识宇宙");
                item.setTotalWords(0);
                item.setDueWords(0);
                responses.add(item);
            }
        }

        // 补充尚未落库但仍在后台构建或待重试的自定义场景任务，保障刷新后下拉选项不丢失
        List<AiAsyncTask> scenarioTasks = aiAsyncTaskService.findRecentByRelatedJobAndTypes(
                userId, wordbook.getId(), List.of(AiTaskConstants.TYPE_RUMINATION_SCENARIO), 20);
        for (AiAsyncTask task : scenarioTasks) {
            if (!List.of(AiTaskConstants.STATUS_PENDING, AiTaskConstants.STATUS_RUNNING,
                    AiTaskConstants.STATUS_RETRY_WAIT, AiTaskConstants.STATUS_FAILED,
                    AiTaskConstants.STATUS_ATTENTION_REQUIRED).contains(task.getStatus())) {
                continue;
            }
            RuminationScenarioResponse pendingMeta = parseScenarioTaskMeta(task);
            if (pendingMeta != null && responses.stream().noneMatch(r -> r.getCode().equals(pendingMeta.getCode()))) {
                responses.add(pendingMeta);
            }
        }
        return responses;
    }

    /** 获取指定场景下的星系图谱拓扑数据（包含核心考纲词与 4D 衍生分支）。 */
    public RuminationGraphResponse getGraph(Long userId, Long wordbookId, String scenarioCode) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, wordbookId);
        String resolvedScenario = StringUtils.hasText(scenarioCode) ? scenarioCode : "produce_kitchen";

        // 优先检查用户是否在当前场景持久化保存过专属星系图谱与词袋
        LearningRuminationGraph savedGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbook.getId())
                        .eq(LearningRuminationGraph::getScenarioCode, resolvedScenario)
                        .eq(LearningRuminationGraph::getDeleted, 0)
        );
        if (savedGraph != null && StringUtils.hasText(savedGraph.getGraphNodesJson())) {
            String fallbackName = "produce_kitchen".equals(resolvedScenario) ? "生鲜采购与厨房料理" : "场景星网";
            String fallbackIcon = "produce_kitchen".equals(resolvedScenario) ? "🥦" : "🏷️";
            RuminationGraphResponse saved = graphPersistenceService.deserializeSavedGraph(
                    savedGraph, fallbackName, fallbackIcon);

            // 自动治理：若历史平铺单字全量误匹配场景（如 custom_1794574697199）导致核心节点超标膨胀，自动清洗治理并落库更新
            boolean isFlatBloated = saved.getNodes() != null
                    && saved.getNodes().stream().filter(n -> "core".equals(n.getNodeType())).count() > MAX_CUSTOM_SCENARIO_CORE_WORDS + 5
                    && saved.getNodes().stream().noneMatch(n -> "expansion".equals(n.getNodeType()));
            if ("custom_1794574697199".equals(resolvedScenario) || isFlatBloated) {
                saved = governAndCleanBloatedGraph(userId, wordbook.getId(), savedGraph, saved);
            }

            saved.setBuilding(false);
            return saved;
        }

        // 若存在正在后台异步构建或等待重试的自定义场景任务，返回轻量占位中心节点而不重复调用 AI
        if (!"produce_kitchen".equals(resolvedScenario)) {
            AiAsyncTask pendingTask = aiAsyncTaskService.findLatestByJobAndScenario(
                    userId, AiTaskConstants.TYPE_RUMINATION_SCENARIO, wordbook.getId(), resolvedScenario);
            if (pendingTask != null) {
                return buildBuildingPlaceholderGraph(resolvedScenario, pendingTask);
            }
            RuminationCustomScenarioRequest customReq = new RuminationCustomScenarioRequest();
            customReq.setWordbookId(wordbook.getId());
            customReq.setScenarioCode(resolvedScenario);
            customReq.setScenarioName("场景星网");
            customReq.setScenarioIcon("🏷️");
            customReq.setScenarioPrompt("自定义语境高关联词汇生态");
            return initCustomScenario(userId, customReq);
        }

        return buildPresetGraphSnapshot(userId, wordbook.getId(), resolvedScenario);
    }

    /** 构建预置参考场景的完整内存拓扑快照，用于首次浏览或异步裂变初次持久化落库。 */
    public RuminationGraphResponse buildPresetGraphSnapshot(Long userId, Long wordbookId, String scenarioCode) {
        String resolved = StringUtils.hasText(scenarioCode) ? scenarioCode : "produce_kitchen";
        RuminationScenarioResponse meta = SCENARIOS_METADATA.stream()
                .filter(s -> s.getCode().equals(resolved))
                .findFirst()
                .orElse(SCENARIOS_METADATA.get(0));
        List<String> candidateTerms = SCENARIO_TERMS.getOrDefault(meta.getCode(), List.of());
        List<LearningWordbookEntry> userEntries = wordbookService.findOwnedEntriesByTerms(
                userId, wordbookId, candidateTerms);
        RuminationGraphResponse response = graphPersistenceService.buildPresetGraph(meta, candidateTerms, userEntries);
        response.setBuilding(false);
        response.setVersion(null);
        return response;
    }

    /**
     * 自定义新场景宇宙初始化（兼容直连调用）。
     * <p>在事务外结合默认 AI 模型与领域语义评估模型筛选高关联度考纲核心词，
     * 构建核心词与 4D 衍生星系拓扑，并最终在独立数据库事务内幂等持久化保存。</p>
     */
    public RuminationGraphResponse initCustomScenario(Long userId, RuminationCustomScenarioRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());

        LearningRuminationGraph savedGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbook.getId())
                        .eq(LearningRuminationGraph::getScenarioCode, request.getScenarioCode())
                        .eq(LearningRuminationGraph::getDeleted, 0)
        );
        if (savedGraph != null && StringUtils.hasText(savedGraph.getGraphNodesJson())) {
            return graphPersistenceService.deserializeSavedGraph(
                    savedGraph, request.getScenarioName(), request.getScenarioIcon());
        }

        RuminationScenarioCheckpoint candidate = buildCustomScenarioCandidate(
                userId, wordbook.getId(), request.getScenarioCode(),
                request.getScenarioName(), request.getScenarioIcon(), request.getScenarioPrompt());

        try {
            graphPersistenceService.persistCustomScenarioForTask(userId, wordbook.getId(), candidate);
        } catch (Exception e) {
            log.error("持久化保存自定义场景星系拓扑失败: userId={} scenario={}", userId, request.getScenarioCode(), e);
            throw LearningAssistantException.system(
                    LearningErrorCode.SYSTEM_UNEXPECTED, "持久化保存自定义场景星系图谱失败，请稍后重试", e);
        }

        RuminationGraphResponse response = new RuminationGraphResponse();
        response.setScenarioCode(candidate.scenarioCode());
        response.setScenarioName(candidate.scenarioName());
        response.setScenarioIcon(candidate.scenarioIcon());
        response.setNodes(candidate.nodes());
        response.setEdges(candidate.edges());
        response.setBagTerms(List.of());
        response.setBuilding(false);
        response.setVersion(0);
        return response;
    }

    /** 在事务外扫描词本与调用模型构建自定义场景星系候选拓扑，供同步与异步任务复用。 */
    public RuminationScenarioCheckpoint buildCustomScenarioCandidate(
            Long userId, Long wordbookId, String scenarioCode,
            String scenarioName, String scenarioIcon, String scenarioPrompt) {
        List<LearningWordbookEntry> allUserEntries = wordbookService.listAllOwnedEntries(userId, wordbookId);
        List<RuminationSemanticEvaluator.ScoredEntry> scoredEntries = semanticEvaluator.evaluateRelevance(
                allUserEntries, scenarioName, scenarioPrompt);

        // 严格按语义关联评分倒序截断，最高保留 MAX_CUSTOM_SCENARIO_CORE_WORDS 个优质核心词，防止节点过载成环
        List<LearningWordbookEntry> matchedCoreEntries = scoredEntries.stream()
                .filter(se -> se.relevanceScore() >= 0.40)
                .limit(MAX_CUSTOM_SCENARIO_CORE_WORDS)
                .map(RuminationSemanticEvaluator.ScoredEntry::entry)
                .toList();

        RuminationAiGenerator.GeneratedUniverseResult aiUniverse = aiGenerator.generateCustomScenarioUniverse(
                userId, scenarioName, scenarioPrompt, allUserEntries, matchedCoreEntries);

        List<RuminationNodeResponse> nodes = new ArrayList<>();
        List<RuminationEdgeResponse> edges = new ArrayList<>();

        String resolvedIcon = StringUtils.hasText(scenarioIcon) ? scenarioIcon.trim() : "🏷️";
        RuminationNodeResponse hubNode = new RuminationNodeResponse();
        hubNode.setId("hub");
        hubNode.setNodeType("hub");
        hubNode.setTerm(scenarioName);
        hubNode.setLabel(resolvedIcon + " " + scenarioName);
        hubNode.setSubLabel("场景核心");
        hubNode.setStatus("mastered");
        nodes.add(hubNode);

        Map<String, LearningWordbookEntry> entryMap = allUserEntries.stream()
                .collect(Collectors.toMap(e -> e.getNormalizedTerm().toLowerCase(Locale.ROOT), e -> e, (a, b) -> a));

        List<String> coreTerms = new ArrayList<>();
        for (LearningWordbookEntry e : matchedCoreEntries) {
            String norm = e.getNormalizedTerm().toLowerCase(Locale.ROOT);
            if (!coreTerms.contains(norm)) coreTerms.add(norm);
        }
        Map<String, String> aiCoreDefs = aiUniverse != null && aiUniverse.coreWordDefinitions() != null
                ? aiUniverse.coreWordDefinitions() : Map.of();
        for (String aiTerm : aiCoreDefs.keySet()) {
            if (coreTerms.size() >= MAX_CUSTOM_SCENARIO_CORE_WORDS) break;
            String norm = aiTerm.toLowerCase(Locale.ROOT);
            if (!coreTerms.contains(norm)) coreTerms.add(norm);
        }
        if (coreTerms.isEmpty() && aiCoreDefs.isEmpty()) {
            List<String> domainDefaults = semanticEvaluator.extractDomainKeywords(scenarioName, scenarioPrompt);
            for (String defTerm : domainDefaults) {
                if (coreTerms.size() >= MAX_CUSTOM_SCENARIO_CORE_WORDS) break;
                if (!coreTerms.contains(defTerm.toLowerCase(Locale.ROOT))) {
                    coreTerms.add(defTerm.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (coreTerms.size() > MAX_CUSTOM_SCENARIO_CORE_WORDS) {
            coreTerms = new ArrayList<>(coreTerms.subList(0, MAX_CUSTOM_SCENARIO_CORE_WORDS));
        }

        for (String term : coreTerms) {
            LearningWordbookEntry entry = entryMap.get(term.toLowerCase(Locale.ROOT));
            String fallbackDef = aiCoreDefs.getOrDefault(term, graphPersistenceService.extractDefinition(null, term));
            RuminationNodeResponse node = graphPersistenceService.buildCoreOrExpansionNode(
                    entry != null ? String.valueOf(entry.getId()) : "core-" + term.replace(" ", "_"),
                    "core",
                    term,
                    entry != null ? graphPersistenceService.extractDefinition(entry, aiCoreDefs.getOrDefault(term, term)) : fallbackDef,
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

        if (aiUniverse != null && aiUniverse.derivations() != null) {
            populateAiDerivations(aiUniverse.derivations(), nodes, edges, entryMap);
        }
        semanticEvaluator.populateDynamic4DDerivations(scenarioName, scenarioPrompt, nodes, edges, entryMap);

        String source = aiUniverse != null && aiUniverse.aiPowered() ? "ai" : "fallback";
        return new RuminationScenarioCheckpoint(scenarioCode, scenarioName, resolvedIcon, source, nodes, edges);
    }

    /** 校验版本并发保存场景星系图谱拓扑与词袋状态；若服务端存在更新版本则抛出 409 冲突。 */
    public RuminationGraphSaveResponse saveGraph(Long userId, RuminationGraphSaveRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        return graphPersistenceService.saveGraphWithVersion(userId, wordbook.getId(), request);
    }

    /** 增量更新部分节点固定坐标，保留并发衍生裂变与词袋变更，不触发整图覆盖。 */
    public RuminationGraphSaveResponse updateNodePositions(Long userId, RuminationGraphPositionsUpdateRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        return graphPersistenceService.updateNodePositions(
                userId, wordbook.getId(), request,
                presetSeedSupplierIfPreset(userId, wordbook.getId(), request.getScenarioCode()));
    }

    /** 增量加入或移除单个词袋词条，保留并发节点拓扑变化。 */
    public RuminationGraphSaveResponse updateBagItem(Long userId, RuminationGraphBagUpdateRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        return graphPersistenceService.updateBagItem(
                userId, wordbook.getId(), request,
                presetSeedSupplierIfPreset(userId, wordbook.getId(), request.getScenarioCode()));
    }

    private java.util.function.Supplier<RuminationGraphResponse> presetSeedSupplierIfPreset(
            Long userId, Long wordbookId, String scenarioCode) {
        boolean isPreset = SCENARIOS_METADATA.stream().anyMatch(s -> s.getCode().equals(scenarioCode));
        return isPreset ? () -> buildPresetGraphSnapshot(userId, wordbookId, scenarioCode) : null;
    }

    /** 查询指定场景下已生成的场景材料文章列表。 */
    public List<RuminationMaterialResponse> listMaterials(Long userId, Long wordbookId, String scenarioCode) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, wordbookId);
        List<LearningRuminationMaterial> materials = materialMapper.selectList(
                new LambdaQueryWrapper<LearningRuminationMaterial>()
                        .eq(LearningRuminationMaterial::getUserId, userId)
                        .eq(LearningRuminationMaterial::getWordbookId, wordbook.getId())
                        .eq(StringUtils.hasText(scenarioCode), LearningRuminationMaterial::getScenarioCode, scenarioCode)
                        .eq(LearningRuminationMaterial::getDeleted, 0)
                        .orderByDesc(LearningRuminationMaterial::getCreateTime)
        );
        List<RuminationMaterialResponse> responses = new ArrayList<>();
        for (LearningRuminationMaterial m : materials) {
            responses.add(persistenceService.mapMaterialToResponse(m));
        }
        return responses;
    }

    /** 查询单个场景材料文章详情与微环境探针状态，严格校验归属权。 */
    public RuminationMaterialResponse getMaterialDetail(Long userId, Long materialId) {
        LearningRuminationMaterial material = requireOwnedMaterial(userId, materialId);
        RuminationMaterialResponse response = persistenceService.mapMaterialToResponse(material);
        response.setBagConsumed(resolveMaterialBagConsumed(userId, material));
        return response;
    }

    /** 校验并获取当前用户名下的场景材料实体，不存在或越权访问时抛出 404 业务异常。 */
    public LearningRuminationMaterial requireOwnedMaterial(Long userId, Long materialId) {
        LearningRuminationMaterial material = materialMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationMaterial>()
                        .eq(LearningRuminationMaterial::getId, materialId)
                        .eq(LearningRuminationMaterial::getUserId, userId)
                        .eq(LearningRuminationMaterial::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (material == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料文章不存在或无权访问");
        }
        return material;
    }

    /** 从已有场景材料中解析并规范化去重目标词汇列表。 */
    public List<String> readMaterialTargetWords(LearningRuminationMaterial material) {
        return persistenceService.readTargetWords(material);
    }

    private Boolean resolveMaterialBagConsumed(Long userId, LearningRuminationMaterial material) {
        if (material == null || material.getTaskId() == null) {
            return Boolean.TRUE;
        }
        try {
            AiAsyncTask task = aiAsyncTaskService.require(userId, material.getTaskId());
            if (task == null || !StringUtils.hasText(task.getPayloadJson())) {
                return Boolean.TRUE;
            }
            JsonNode payloadNode = objectMapper.readTree(task.getPayloadJson());
            return !payloadNode.has("consumeBag") || payloadNode.path("consumeBag").asBoolean(true);
        } catch (Exception ignored) {
            return Boolean.TRUE;
        }
    }

    /**
     * 针对词袋中维护的词汇调用默认 AI 模型编织场景材料文章与微环境探针。
     * <p>严禁在数据库事务内调用 AI 接口；先完成模型生成与降级兜底，再在独立事务内幂等落库并按快照合并扣除词袋。</p>
     */
    public RuminationMaterialResponse generateMaterial(Long userId, RuminationMaterialGenerateRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        List<String> snapshotTargetWords = normalizeDistinctTargetWords(request.getTargetWords());
        if (snapshotTargetWords.size() < 5) {
            throw LearningAssistantException.badRequest(LearningErrorCode.ARTICLE_WORDS_INVALID, "词袋中目标词汇去重后至少需要 5 个才能编织场景材料");
        }

        // 1. 解析并校验场景归属、场景名称与语境提示词
        String[] scenarioMeta = resolveOwnedScenarioMeta(userId, wordbook.getId(), request.getScenarioCode());
        String scenarioName = scenarioMeta[0];
        String scenarioPrompt = scenarioMeta[1];

        // 2. 获取本次目标词在词本中的中文释义映射快照
        Map<String, String> wordDefinitions = snapshotWordDefinitions(userId, wordbook.getId(), snapshotTargetWords);

        // 3. 在数据库事务外调用默认 AI 模型联合生成场景材料与微环境探针（失败自动平滑降级）
        RuminationAiGenerator.GeneratedMaterialResult generated = aiGenerator.generateMaterialAndProbes(
                userId, scenarioName, scenarioPrompt, snapshotTargetWords, wordDefinitions);

        // 4. 在独立事务中保存材料、探针并按快照增量扣除词袋原料
        String generationSource = generated.aiPowered() ? "ai" : "fallback";
        return persistenceService.persistGeneratedMaterial(
                userId, wordbook.getId(), request.getScenarioCode(),
                snapshotTargetWords, generationSource, null, generated);
    }

    /**
     * 校验场景在当前单词本中归属有效，并返回场景名称与语境提示词快照。
     *
     * @param userId       当前用户 ID
     * @param wordbookId   当前单词本 ID
     * @param scenarioCode 场景编码
     * @return {@code [scenarioName, scenarioPrompt]} 二元组
     */
    public String[] resolveOwnedScenarioMeta(Long userId, Long wordbookId, String scenarioCode) {
        String normalizedCode = scenarioCode == null ? "" : scenarioCode.trim();
        RuminationScenarioResponse presetMeta = SCENARIOS_METADATA.stream()
                .filter(s -> s.getCode().equals(normalizedCode))
                .findFirst()
                .orElse(null);
        if (presetMeta != null) {
            return new String[]{presetMeta.getName(), presetMeta.getName() + "日常情境与交流"};
        }
        LearningRuminationGraph savedGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbookId)
                        .eq(LearningRuminationGraph::getScenarioCode, normalizedCode)
                        .eq(LearningRuminationGraph::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (savedGraph == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "反刍场景不存在或无权访问");
        }
        return extractScenarioMetaFromGraph(savedGraph);
    }

    /**
     * 读取并封装本次生成所需词汇在当前词本中的中文释义快照。
     *
     * @param userId      当前用户 ID
     * @param wordbookId  当前单词本 ID
     * @param targetWords 归一化后的目标词汇列表
     * @return {@code word -> definition} 映射
     */
    public Map<String, String> snapshotWordDefinitions(Long userId, Long wordbookId, List<String> targetWords) {
        List<String> normalized = normalizeDistinctTargetWords(targetWords);
        List<LearningWordbookEntry> ownedEntries = wordbookService.findOwnedEntriesByTerms(
                userId, wordbookId, normalized);
        Map<String, String> wordDefinitions = new HashMap<>();
        for (LearningWordbookEntry entry : ownedEntries) {
            String def = semanticEvaluator.extractEntryDefinition(entry);
            if (StringUtils.hasText(def)) {
                wordDefinitions.put(entry.getNormalizedTerm().toLowerCase(Locale.ROOT), def);
            }
        }
        for (String w : normalized) {
            wordDefinitions.putIfAbsent(w, semanticEvaluator.commonChineseTerm(w));
        }
        return wordDefinitions;
    }

    /** 将待生成词袋词汇清洗为小写去重有序列表。 */
    public List<String> normalizeDistinctTargetWords(List<String> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) {
            return List.of();
        }
        return rawWords.stream()
                .filter(StringUtils::hasText)
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /**
     * 为选中词汇节点执行默认 AI 模型实时 4D 衍生裂变，并绑定词本真实掌握状态（兼容直连调用）。
     *
     * @param userId  当前用户 ID
     * @param request 裂变请求参数
     * @return 新增的 4D 衍生节点与连线
     */
    public RuminationFissionResponse fissionNode(Long userId, RuminationFissionRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());
        String scenarioName = StringUtils.hasText(request.getScenarioName())
                ? request.getScenarioName()
                : resolveScenarioMeta(userId, wordbook.getId(), request.getScenarioCode())[0];

        RuminationFissionCheckpoint checkpoint = buildFissionCandidate(
                userId,
                wordbook.getId(),
                request.getScenarioCode(),
                scenarioName,
                StringUtils.hasText(request.getParentNodeId()) ? request.getParentNodeId() : request.getTargetWord(),
                request.getTargetWord(),
                request.getTargetDefinition(),
                request.getExistingTerms()
        );

        RuminationFissionResponse response = new RuminationFissionResponse();
        response.setWordbookId(String.valueOf(wordbook.getId()));
        response.setScenarioCode(request.getScenarioCode());
        response.setParentNodeId(checkpoint.parentNodeId());
        response.setTargetWord(checkpoint.targetWord());
        response.setAiPowered(!"fallback".equalsIgnoreCase(checkpoint.generationSource()));
        response.setNodes(checkpoint.candidateNodes());
        response.setEdges(checkpoint.candidateEdges());
        return response;
    }

    /** 在事务外执行节点 4D 衍生模型生成并绑定个人词本真实状态，供同步和异步任务复用。 */
    public RuminationFissionCheckpoint buildFissionCandidate(
            Long userId, Long wordbookId, String scenarioCode, String scenarioName,
            String parentNodeId, String targetWord, String targetDefinition, List<String> existingTermsList) {
        Set<String> existingTerms = new LinkedHashSet<>();
        if (existingTermsList != null) {
            for (String term : existingTermsList) {
                if (StringUtils.hasText(term)) {
                    existingTerms.add(term.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        RuminationAiGenerator.FissionResult result = aiGenerator.generateFissionExpansions(
                userId, scenarioName, targetWord, targetDefinition, existingTerms);

        List<String> generatedTerms = result.expansions().stream()
                .map(RuminationAiGenerator.DerivationDraft::term)
                .toList();
        Map<String, LearningWordbookEntry> entryMap = generatedTerms.isEmpty()
                ? Map.of()
                : wordbookService.findOwnedEntriesByTerms(userId, wordbookId, generatedTerms).stream()
                        .collect(Collectors.toMap(e -> e.getNormalizedTerm().toLowerCase(Locale.ROOT), e -> e, (a, b) -> a));

        String resolvedParentId = StringUtils.hasText(parentNodeId) ? parentNodeId : targetWord;
        List<RuminationNodeResponse> nodes = new ArrayList<>();
        List<RuminationEdgeResponse> edges = new ArrayList<>();

        for (RuminationAiGenerator.DerivationDraft item : result.expansions()) {
            String nodeId = "exp-" + item.term().replace(" ", "_");
            LearningWordbookEntry entry = entryMap.get(item.term().toLowerCase(Locale.ROOT));
            nodes.add(graphPersistenceService.buildCoreOrExpansionNode(
                    nodeId,
                    "expansion",
                    item.term(),
                    entry != null
                            ? graphPersistenceService.extractDefinition(entry, item.definition())
                            : item.definition(),
                    item.dimension(),
                    entry
            ));
            RuminationEdgeResponse edge = new RuminationEdgeResponse();
            edge.setSource(resolvedParentId);
            edge.setTarget(nodeId);
            edge.setRelationType(item.dimension());
            edge.setLabel("");
            edges.add(edge);
        }

        return new RuminationFissionCheckpoint(
                wordbookId,
                scenarioCode,
                scenarioName,
                resolvedParentId,
                targetWord,
                result.aiPowered() ? "ai" : "fallback",
                nodes,
                edges
        );
    }

    /** 校验在已保存星系图谱中指定的非枢纽父节点是否仍然存在。 */
    public void assertParentNodeExistsIfSaved(Long userId, Long wordbookId, String scenarioCode, String parentNodeId) {
        if (!StringUtils.hasText(parentNodeId) || "hub".equals(parentNodeId)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "请选择一个具体的词汇节点执行衍生裂变");
        }
        LearningRuminationGraph savedGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbookId)
                        .eq(LearningRuminationGraph::getScenarioCode, scenarioCode)
                        .eq(LearningRuminationGraph::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (savedGraph == null || !StringUtils.hasText(savedGraph.getGraphNodesJson())) {
            return;
        }
        try {
            JsonNode nodes = objectMapper.readTree(savedGraph.getGraphNodesJson());
            for (JsonNode n : nodes) {
                if (parentNodeId.equals(n.path("id").asText()) && !"hub".equals(n.path("nodeType").asText())) {
                    return;
                }
            }
        } catch (Exception ex) {
            return;
        }
        throw LearningAssistantException.notFound(
                LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "待裂变的父节点在当前场景中已不存在");
    }

    private RuminationScenarioResponse parseScenarioTaskMeta(AiAsyncTask task) {
        if (task == null || !StringUtils.hasText(task.getPayloadJson())) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(task.getPayloadJson());
            String code = payload.path("scenarioCode").asText("");
            String name = payload.path("scenarioName").asText("场景星网");
            String icon = payload.path("scenarioIcon").asText("🏷️");
            if (!StringUtils.hasText(code)) {
                return null;
            }
            RuminationScenarioResponse res = new RuminationScenarioResponse();
            res.setCode(code);
            res.setName(name);
            res.setIcon(icon);
            res.setDescription("正在后台构建自定义语境星系...");
            res.setTotalWords(0);
            res.setDueWords(0);
            return res;
        } catch (Exception ex) {
            return null;
        }
    }

    private RuminationGraphResponse buildBuildingPlaceholderGraph(String scenarioCode, AiAsyncTask task) {
        RuminationScenarioResponse meta = parseScenarioTaskMeta(task);
        String scName = meta != null ? meta.getName() : "场景星网";
        String scIcon = meta != null ? meta.getIcon() : "🏷️";
        RuminationGraphResponse response = new RuminationGraphResponse();
        response.setScenarioCode(scenarioCode);
        response.setScenarioName(scName);
        response.setScenarioIcon(scIcon);
        response.setBuilding(List.of(
                AiTaskConstants.STATUS_PENDING,
                AiTaskConstants.STATUS_RUNNING,
                AiTaskConstants.STATUS_RETRY_WAIT).contains(task.getStatus()));
        RuminationNodeResponse hub = new RuminationNodeResponse();
        hub.setId("hub");
        hub.setNodeType("hub");
        hub.setTerm(scName);
        hub.setLabel(scIcon + " " + scName);
        hub.setSubLabel("后台构建中");
        hub.setStatus("mastered");
        response.setNodes(List.of(hub));
        response.setEdges(List.of());
        response.setBagTerms(List.of());
        return response;
    }

    private String[] resolveScenarioMeta(Long userId, Long wordbookId, String scenarioCode) {
        RuminationScenarioResponse presetMeta = SCENARIOS_METADATA.stream()
                .filter(s -> s.getCode().equals(scenarioCode))
                .findFirst()
                .orElse(null);
        if (presetMeta != null) {
            return new String[]{presetMeta.getName(), presetMeta.getName() + "日常情境与交流"};
        }
        LearningRuminationGraph savedGraph = graphMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationGraph>()
                        .eq(LearningRuminationGraph::getUserId, userId)
                        .eq(LearningRuminationGraph::getWordbookId, wordbookId)
                        .eq(LearningRuminationGraph::getScenarioCode, scenarioCode)
                        .eq(LearningRuminationGraph::getDeleted, 0)
        );
        return savedGraph != null
                ? extractScenarioMetaFromGraph(savedGraph)
                : new String[]{"场景星网", "自定义语境高关联词汇生态"};
    }

    private String[] extractScenarioMetaFromGraph(LearningRuminationGraph savedGraph) {
        String scenarioName = "场景星网";
        String scenarioPrompt = "自定义语境高关联词汇生态";
        if (savedGraph != null && StringUtils.hasText(savedGraph.getGraphNodesJson())) {
            try {
                JsonNode nodesJson = objectMapper.readTree(savedGraph.getGraphNodesJson());
                for (JsonNode n : nodesJson) {
                    if ("hub".equals(n.path("nodeType").asText())) {
                        String label = n.path("label").asText();
                        if (StringUtils.hasText(label)) {
                            scenarioName = label.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "").trim();
                            scenarioPrompt = scenarioName + "真实场景交流与语境实践";
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return new String[]{scenarioName, scenarioPrompt};
    }

    private void populateAiDerivations(List<RuminationAiGenerator.DerivationDraft> drafts,
                                       List<RuminationNodeResponse> nodes,
                                       List<RuminationEdgeResponse> edges,
                                       Map<String, LearningWordbookEntry> entryMap) {
        Set<String> existingIds = nodes.stream().map(RuminationNodeResponse::getId).collect(Collectors.toSet());
        Set<String> existingTerms = nodes.stream()
                .map(n -> n.getTerm() != null ? n.getTerm().toLowerCase(Locale.ROOT) : "")
                .collect(Collectors.toSet());
        for (RuminationAiGenerator.DerivationDraft draft : drafts) {
            RuminationNodeResponse targetCore = nodes.stream()
                    .filter(n -> "core".equals(n.getNodeType()) && draft.linkedCore().equalsIgnoreCase(n.getTerm()))
                    .findFirst()
                    .orElse(null);
            if (targetCore == null) continue;
            String nodeId = "exp-" + draft.term().replace(" ", "_");
            if (existingIds.contains(nodeId) || existingTerms.contains(draft.term().toLowerCase(Locale.ROOT))) continue;

            LearningWordbookEntry entry = entryMap.get(draft.term().toLowerCase(Locale.ROOT));
            nodes.add(graphPersistenceService.buildCoreOrExpansionNode(
                    nodeId, "expansion", draft.term(), draft.definition(), draft.dimension(), entry));
            existingIds.add(nodeId);
            existingTerms.add(draft.term().toLowerCase(Locale.ROOT));

            RuminationEdgeResponse edge = new RuminationEdgeResponse();
            edge.setSource(targetCore.getId());
            edge.setTarget(nodeId);
            edge.setRelationType(draft.dimension());
            edge.setLabel("");
            edges.add(edge);
        }
    }

    /** 提交微场景探针作答并结算复习记录；采用行锁 + 条件原子更新杜绝并发重复结算。 */
    @Transactional(rollbackFor = Exception.class)
    public RuminationProbeSubmitResponse submitProbe(Long userId, RuminationProbeSubmitRequest request) {
        LearningWordbook wordbook = wordbookService.requireOwnedWordbook(userId, request.getWordbookId());

        // 1. 加行级排他锁读取探针题目并核验归属，阻塞并发请求读取旧快照
        LearningRuminationProbe probe = probeMapper.selectByIdForUpdate(request.getProbeId(), userId);
        if (probe == null || !userId.equals(probe.getUserId()) || Boolean.TRUE.equals(probe.getDeleted())) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "学习结果检查题目不存在或无权访问");
        }

        // 核验探针所属的场景材料归属
        LearningRuminationMaterial material = materialMapper.selectById(probe.getMaterialId());
        if (material == null || !wordbook.getId().equals(material.getWordbookId())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID, "探针所属场景材料与当前词本不匹配");
        }

        String targetWord = probe.getTargetWord().trim().toLowerCase(Locale.ROOT);

        // 2. 重复结算幂等保护
        if (probe.getAnsweredTime() != null && probe.getIsCorrect() != null) {
            return buildIdempotentProbeResponse(userId, wordbook.getId(), probe, targetWord);
        }

        // 3. 服务端独立裁定判分
        String selected = request.getSelectedAnswer() != null ? request.getSelectedAnswer().trim() : "";
        String correct = probe.getCorrectAnswer() != null ? probe.getCorrectAnswer().trim() : "";
        boolean isCorrect = StringUtils.hasText(selected) && selected.equalsIgnoreCase(correct);
        LocalDateTime answeredTime = LocalDateTime.now();

        // 4. 条件原子落库（仅 answered_time IS NULL 允许写入一次）
        int updated = probeMapper.markAnsweredAtomically(
                probe.getId(),
                userId,
                selected,
                isCorrect,
                isCorrect ? 10 : 0,
                isCorrect,
                answeredTime
        );
        if (updated == 0) {
            LearningRuminationProbe latest = probeMapper.selectByIdForUpdate(probe.getId(), userId);
            return buildIdempotentProbeResponse(
                    userId, wordbook.getId(), latest != null ? latest : probe, targetWord);
        }

        // 5. 首次作答正规结算词本复习周期
        RuminationProbeSubmitResponse response = new RuminationProbeSubmitResponse();
        response.setTargetWord(targetWord);
        response.setIsCorrect(isCorrect);
        response.setCorrectAnswer(correct);
        response.setScoreGained(isCorrect ? 10 : 0);
        response.setIlluminated(isCorrect);
        response.setAlreadyAnswered(false);

        List<LearningWordbookEntry> entries = wordbookService.findOwnedEntriesByTerms(
                userId, wordbook.getId(), List.of(targetWord));

        if (entries.isEmpty()) {
            AddWordbookEntryRequest addReq = new AddWordbookEntryRequest();
            addReq.setTerm(targetWord);
            addReq.setNote("知识反刍场景拓展词汇");
            wordbookService.addEntry(userId, wordbook.getId(), addReq);
            entries = wordbookService.findOwnedEntriesByTerms(userId, wordbook.getId(), List.of(targetWord));
        }

        if (!entries.isEmpty()) {
            LearningWordbookEntry entry = entries.get(0);
            response.setEntryId(entry.getId());

            ReviewSubmitRequest reviewRequest = new ReviewSubmitRequest();
            reviewRequest.setResult(isCorrect ? "remembered" : "vague");
            reviewRequest.setAssessmentType("rumination_probe");
            reviewRequest.setAnswerText(selected);
            reviewRequest.setCorrectAnswer(correct);
            reviewRequest.setCheckResult(isCorrect ? "correct" : "wrong");
            reviewRequest.setScore(isCorrect ? 100 : 0);
            reviewRequest.setDurationMillis(request.getDurationMillis());
            reviewRequest.setDurationSeconds((int) Math.max(1, (request.getDurationMillis() == null ? 1000 : request.getDurationMillis()) / 1000));

            ReviewSubmitResponse reviewOutcome = wordbookService.submitReview(userId, entry.getId(), reviewRequest);
            response.setMasteryScoreAfter(reviewOutcome.getMasteryScore());
            response.setReviewStageAfter(reviewOutcome.getReviewStage());
            response.setNextReviewTime(reviewOutcome.getNextReviewTime());

            log.info("用户「{}」完成知识反刍微探针「{}」(probeId={})，服务端独立判分「{}」，熟练度更新为 {}，下次复习时间 {}",
                    userId, targetWord, probe.getId(), isCorrect ? "正确" : "错误",
                    reviewOutcome.getMasteryScore(), reviewOutcome.getNextReviewTime());
        }

        return response;
    }

    private RuminationProbeSubmitResponse buildIdempotentProbeResponse(
            Long userId, Long wordbookId, LearningRuminationProbe probe, String targetWord) {
        log.info("用户「{}」重复提交微探针 probeId={} (target={})，执行幂等响应", userId, probe.getId(), targetWord);
        RuminationProbeSubmitResponse idempotentRes = new RuminationProbeSubmitResponse();
        idempotentRes.setTargetWord(targetWord);
        idempotentRes.setIsCorrect(probe.getIsCorrect());
        idempotentRes.setCorrectAnswer(probe.getCorrectAnswer());
        idempotentRes.setScoreGained(0);
        idempotentRes.setIlluminated(Boolean.TRUE.equals(probe.getIsCorrect()));
        idempotentRes.setAlreadyAnswered(true);

        List<LearningWordbookEntry> entries = wordbookService.findOwnedEntriesByTerms(
                userId, wordbookId, List.of(targetWord));
        if (!entries.isEmpty()) {
            LearningWordbookEntry entry = entries.get(0);
            idempotentRes.setEntryId(entry.getId());
            idempotentRes.setMasteryScoreAfter(entry.getMasteryScore());
            idempotentRes.setReviewStageAfter(entry.getReviewStage());
            idempotentRes.setNextReviewTime(entry.getNextReviewTime());
        }
        return idempotentRes;
    }

    /**
     * 自动治理并瘦身过度膨胀的历史自定义场景星系图谱（委托持久化服务处理）。
     */
    public RuminationGraphResponse governAndCleanBloatedGraph(
            Long userId, Long wordbookId, LearningRuminationGraph savedGraph, RuminationGraphResponse saved) {
        List<LearningWordbookEntry> allUserEntries = wordbookService.listAllOwnedEntries(userId, wordbookId);
        return graphPersistenceService.governAndCleanBloatedGraph(userId, wordbookId, savedGraph, saved, allUserEntries);
    }
}
