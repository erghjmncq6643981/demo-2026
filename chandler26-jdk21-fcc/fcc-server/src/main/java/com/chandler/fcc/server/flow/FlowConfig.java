package com.chandler.fcc.server.flow;

import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.chandler.fcc.server.flow.infrastructure.FlowConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行端已发布流程的懒加载缓存。
 *
 * <p>流程发布只影响新建的流程实例。缓存保存的是经过完整校验的发布快照，
 * 不把缓存对象暴露给通话执行上下文；通话创建时应将该快照序列化到流程实例。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowConfig {

    private static final Duration CACHE_TTL = Duration.ofHours(3);
    private static final Duration CACHE_REFRESH = Duration.ofMinutes(175);

    private final FlowConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final LoadingCache<String, PublishedFlow> publishedFlows = buildCache();

    /**
     * 保留运维手工触发全量失效的能力，但不在应用启动时访问数据库。
     */
    public void loadAllPublishedFlows() {
        publishedFlows.invalidateAll();
        log.info("[流程配置] 已清空运行端流程缓存，后续请求按需加载");
    }

    /**
     * 重新校验并加载指定流程的当前生效版本。
     *
     * @param flowKey 流程唯一键
     * @return 是否成功加载有效发布版本
     */
    public boolean reloadFlow(String flowKey) {
        if (flowKey == null || flowKey.isBlank()) {
            return false;
        }
        try {
            PublishedFlow loaded = loadByFlowKey(flowKey);
            removeOtherKeysFor(loaded.flowKey());
            publishedFlows.put(flowKey, loaded);
            publishedFlows.put(runtimeModelKey(loaded), loaded);
            log.info("[流程配置] 热加载生效 flowKey={}, version={}", flowKey, loaded.versionNo());
            return true;
        } catch (RuntimeException failure) {
            log.warn("[流程配置] 热加载被拒绝 flowKey={} reason={}", flowKey, failure.getMessage());
            return false;
        }
    }

    /**
     * 获取业务模型当前生效的流程名称。首次访问时才查询数据库，刷新失败时 Caffeine 保留旧值。
     *
     * @param modelKey 运行时业务模型标识或流程键
     * @return 数据库中的真实流程名称；未找到时为空
     */
    public Optional<String> getFlowName(String modelKey) {
        if (modelKey == null || modelKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(publishedFlows.get(modelKey).flowName());
        } catch (RuntimeException failure) {
            log.debug("[流程配置] 当前模型没有已发布流程 modelKey={}", modelKey);
            return Optional.empty();
        }
    }

    /**
     * 按运行模板或已固定的版本 ID 获取流程定义快照。
     *
     * @param versionId 已固定的版本 ID，可为空
     * @param template 运行模板或模型键
     * @return 已校验流程快照；不存在时为空
     */
    public Optional<FlowSnapshot> getPublishedFlow(String versionId, String template) {
        String lookupKey = versionId == null || versionId.isBlank()
            ? template
            : "VERSION:" + versionId;
        if (lookupKey == null || lookupKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(publishedFlows.get(lookupKey));
        } catch (RuntimeException failure) {
            log.warn("[流程配置] 无法加载流程快照 lookupKey={}", lookupKey);
            return Optional.empty();
        }
    }

    /**
     * 创建带三小时过期、提前五分钟异步刷新的缓存。
     *
     * @return 流程发布快照缓存
     */
    private LoadingCache<String, PublishedFlow> buildCache() {
        CacheLoader<String, PublishedFlow> loader = new CacheLoader<>() {
            @Override
            public PublishedFlow load(String key) {
                return loadByLookupKey(key);
            }
        };
        return Caffeine.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .refreshAfterWrite(CACHE_REFRESH)
            .executor(ForkJoinPool.commonPool())
            .build(loader);
    }

    /**
     * 按流程键或模型键查询已发布定义。
     *
     * @param lookupKey 流程键或运行时模型键
     * @return 已校验发布快照
     */
    private PublishedFlow loadByLookupKey(String lookupKey) {
        if (lookupKey.startsWith("VERSION:")) {
            Map<String, Object> versionRow = mapper.findByVersionId(lookupKey.substring("VERSION:".length()));
            if (versionRow == null) {
                throw new IllegalArgumentException("未找到流程版本: " + lookupKey);
            }
            return toSnapshot(versionRow);
        }
        Map<String, Object> row = mapper.findPublishedByKey(lookupKey);
        if (row == null) {
            for (String modelType : modelTypeCandidates(lookupKey)) {
                row = mapper.findPublishedByModelType(modelType);
                if (row != null) {
                    break;
                }
            }
        }
        if (row == null) {
            throw new IllegalArgumentException("未找到已发布流程: " + lookupKey);
        }
        return toSnapshot(row);
    }

    /**
     * 按稳定流程键加载并校验流程。
     *
     * @param flowKey 流程键
     * @return 已校验发布快照
     */
    private PublishedFlow loadByFlowKey(String flowKey) {
        Map<String, Object> row = mapper.findPublishedByKey(flowKey);
        if (row == null) {
            throw new IllegalArgumentException("未找到发布版本: " + flowKey);
        }
        return toSnapshot(row);
    }

    /**
     * 将数据库行转换为不可变缓存快照，并校验流程动作目录。
     *
     * @param row Mapper 查询结果
     * @return 已校验快照
     */
    private PublishedFlow toSnapshot(Map<String, Object> row) {
        String flowKey = string(row, "flowKey");
        String definitionId = string(row, "definitionId");
        String versionId = string(row, "versionId");
        String modelType = string(row, "modelType");
        String flowName = string(row, "flowName");
        String definitionJson = string(row, "definitionJson");
        if (flowKey == null || definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("发布流程缺少 flowKey 或 definitionJson");
        }
        if (flowKey.startsWith("SYSTEM_")) {
            validateSystemModel(definitionJson);
        } else {
            FlowDefinitionValidator.validate(definitionJson, modelType);
        }
        Integer versionNo = integer(row, "versionNo");
        return new PublishedFlow(definitionId, versionId, flowKey, modelType, flowName, definitionJson, versionNo);
    }

    /**
     * 删除同一流程通过模型键建立的别名缓存，避免发布后继续返回旧名称。
     *
     * @param flowKey 流程键
     */
    private void removeOtherKeysFor(String flowKey) {
        publishedFlows.asMap().entrySet().removeIf(entry -> flowKey.equals(entry.getValue().flowKey()));
    }

    /**
     * 将数据库流程转换为已有通话上下文使用的模型键。
     *
     * @param flow 流程快照
     * @return 运行时模型键
     */
    private String runtimeModelKey(PublishedFlow flow) {
        if (flow.flowKey().endsWith("INBOUND") || "INBOUND".equalsIgnoreCase(flow.modelType())) {
            return FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
        }
        if (flow.flowKey().endsWith("AGENT_FIRST")
            || flow.flowKey().endsWith("AGENT_ORIGINATED")
            || "AGENT_FIRST".equalsIgnoreCase(flow.modelType())
            || "AGENT_ORIGINATED".equalsIgnoreCase(flow.modelType())) {
            return FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
        }
        if (flow.flowKey().endsWith("NOTIFICATION") || "NOTIFICATION".equalsIgnoreCase(flow.modelType())) {
            return FlowModelType.AUTO_DIAL_NOTIFICATION.name();
        }
        return flow.modelType() == null || flow.modelType().isBlank() ? flow.flowKey() : flow.modelType();
    }

    /**
     * 返回模型键可能对应的数据库模型类型。
     *
     * @param modelKey 运行时模型键
     * @return 候选数据库模型类型
     */
    private List<String> modelTypeCandidates(String modelKey) {
        if (FlowModelType.INBOUND_CUSTOMER_SERVICE.name().equals(modelKey)) {
            return List.of("INBOUND");
        }
        if (FlowModelType.OUTBOUND_TWO_WAY_CALL.name().equals(modelKey)) {
            return List.of("AGENT_FIRST", "AGENT_ORIGINATED", "OUTBOUND");
        }
        if (FlowModelType.AUTO_DIAL_NOTIFICATION.name().equals(modelKey)) {
            return List.of("NOTIFICATION");
        }
        return List.of(modelKey);
    }

    /**
     * 校验固定系统模型包含节点且动作属于公共目录。
     *
     * @param definitionJson 固定模型 JSON
     */
    private void validateSystemModel(String definitionJson) {
        try {
            JsonNode model = objectMapper.readTree(definitionJson);
            JsonNode nodes = model.path("nodes");
            if (!nodes.isArray() || nodes.isEmpty()) {
                throw new IllegalArgumentException("系统通话模型缺少动作节点");
            }
            nodes.forEach(node -> FlowActionType.fromCode(node.path("action").asText()));
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException("系统通话模型不是有效 JSON", failure);
        }
    }

    /**
     * 读取数据库文本字段。
     *
     * @param row 查询结果
     * @param key 字段别名
     * @return 文本值
     */
    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * 读取数据库版本字段。
     *
     * @param row 查询结果
     * @param key 字段别名
     * @return 整数值
     */
    private Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    /**
     * 运行端缓存中的已发布不可变流程。
     *
     * @param definitionId 流程主定义 ID
     * @param versionId 流程版本 ID
     * @param flowKey 流程键
     * @param modelType 数据库模型类型
     * @param flowName 流程名称
     * @param definitionJson 流程 JSON 快照
     * @param versionNo 版本序号
     */
    public static final class PublishedFlow implements FlowSnapshot {

        private final String flowKey;
        private final String definitionId;
        private final String versionId;
        private final String modelType;
        private final String flowName;
        private final String definitionJson;
        private final Integer versionNo;

        private PublishedFlow(
            String definitionId,
            String versionId,
            String flowKey,
            String modelType,
            String flowName,
            String definitionJson,
            Integer versionNo
        ) {
            this.definitionId = definitionId;
            this.versionId = versionId;
            this.flowKey = flowKey;
            this.modelType = modelType;
            this.flowName = flowName;
            this.definitionJson = definitionJson;
            this.versionNo = versionNo;
        }

        @Override
        public String flowKey() {
            return flowKey;
        }

        @Override
        public String definitionId() {
            return definitionId;
        }

        @Override
        public String versionId() {
            return versionId;
        }

        @Override
        public String modelType() {
            return modelType;
        }

        @Override
        public String flowName() {
            return flowName;
        }

        @Override
        public String definitionJson() {
            return definitionJson;
        }

        @Override
        public Integer versionNo() {
            return versionNo;
        }
    }

    /**
     * 运行端流程不可变快照访问契约。
     */
    public interface FlowSnapshot {

        /** @return 流程键 */
        String flowKey();

        /** @return 流程主定义 ID */
        String definitionId();

        /** @return 流程版本 ID */
        String versionId();

        /** @return 模型类型 */
        String modelType();

        /** @return 流程名称 */
        String flowName();

        /** @return 完整定义 JSON */
        String definitionJson();

        /** @return 版本序号 */
        Integer versionNo();
    }
}
