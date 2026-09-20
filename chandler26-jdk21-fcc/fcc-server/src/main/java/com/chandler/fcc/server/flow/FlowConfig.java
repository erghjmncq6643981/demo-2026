package com.chandler.fcc.server.flow;

import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.chandler.fcc.server.flow.infrastructure.FlowConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 加载并校验数据库中已发布的流程定义。
 *
 * <p>固定流程由对应业务应用服务执行，本组件不再编译或调度第二套动态动作链。它只维护
 * 已发布定义的展示元数据，并为管理端发布后的热加载提供明确成功或失败结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowConfig {

    private final FlowConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final Map<String, String> flowNameByModel = new ConcurrentHashMap<>();

    /**
     * 应用启动时加载全部已发布定义。
     */
    @PostConstruct
    public void loadPublishedFlowsOnStartup() {
        try {
            loadAllPublishedFlows();
        } catch (RuntimeException failure) {
            log.warn("[流程配置] 启动加载失败: {}", failure.getMessage());
        }
    }

    /**
     * 校验并加载数据库中的全部已发布流程。
     */
    public synchronized void loadAllPublishedFlows() {
        List<Map<String, Object>> rows = mapper.findAllPublished();
        for (Map<String, Object> row : rows) {
            validateAndApply(row);
        }
        log.info("[流程配置] 已加载 {} 条发布定义", rows.size());
    }

    /**
     * 热重载指定流程的最新发布版本。
     *
     * @param flowKey 流程唯一键
     * @return 是否成功加载有效发布版本
     */
    public synchronized boolean reloadFlow(String flowKey) {
        if (flowKey == null || flowKey.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> row = mapper.findPublishedByKey(flowKey);
            if (row == null) {
                log.warn("[流程配置] 未找到发布版本 flowKey={}", flowKey);
                return false;
            }
            validateAndApply(row);
            log.info("[流程配置] 热加载生效 flowKey={}", flowKey);
            return true;
        } catch (RuntimeException failure) {
            log.warn("[流程配置] 热加载被拒绝 flowKey={} reason={}", flowKey, failure.getMessage());
            return false;
        }
    }

    /**
     * 获取某业务模型当前生效的流程名称。
     *
     * @param modelKey 运行时业务模型标识
     * @return 数据库中的真实流程名称；未加载时为空
     */
    public Optional<String> getFlowName(String modelKey) {
        if (modelKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(flowNameByModel.get(modelKey));
    }

    /**
     * 校验一条发布定义并原子更新展示元数据。
     *
     * @param row Mapper 返回的发布定义摘要
     * @throws IllegalArgumentException 定义字段缺失、JSON 非法或动作不在公共目录
     */
    private void validateAndApply(Map<String, Object> row) {
        String flowKey = string(row, "flowKey");
        String modelType = string(row, "modelType");
        String flowName = string(row, "flowName");
        String definitionJson = string(row, "definitionJson");
        if (flowKey == null || definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("发布流程缺少 flowKey 或 definitionJson");
        }

        if (flowKey.startsWith("SYSTEM_")) {
            validateSystemModel(definitionJson);
        } else {
            FlowDefinitionValidator.validate(definitionJson);
        }
        if (flowName != null && !flowName.isBlank()) {
            flowNameByModel.put(normalizeModelKey(flowKey, modelType), flowName);
        }
    }

    /**
     * 校验固定模型包含节点且每个动作都属于公共动作目录。
     *
     * @param definitionJson 固定模型 JSON
     * @throws IllegalArgumentException 模型结构或动作非法
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
     * 将数据库模型类型映射为通话上下文使用的运行时模型标识。
     *
     * @param flowKey 流程键
     * @param modelType 数据库模型类型
     * @return 运行时模型标识
     */
    private String normalizeModelKey(String flowKey, String modelType) {
        if (flowKey.endsWith("INBOUND") || "INBOUND".equalsIgnoreCase(modelType)) {
            return FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
        }
        if (flowKey.endsWith("AGENT_FIRST") || "AGENT_FIRST".equalsIgnoreCase(modelType)) {
            return FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
        }
        if (flowKey.endsWith("NOTIFICATION") || "NOTIFICATION".equalsIgnoreCase(modelType)) {
            return FlowModelType.AUTO_DIAL_NOTIFICATION.name();
        }
        return modelType == null || modelType.isBlank() ? flowKey : modelType;
    }

    /**
     * 读取 Mapper 结果中的可选文本字段。
     *
     * @param row 查询结果
     * @param key 字段别名
     * @return 文本值；缺失时为空
     */
    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }
}
