package com.chandler.fcc.server.flow;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowModelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 话务流程编排规则库（基于已发布 DB/JSON 定义动态编译与热加载）
 * <p>
 * 定义不同业务模型（如客户呼入客服、坐席双向外呼、自动外呼通知）在各个生命周期阶段需要执行的动作链。
 * 支持管理端发布流程后实时热更新生效。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class FlowConfig {

    private final Map<String, List<FlowNode>> flowMap = new ConcurrentHashMap<>();

    /**
     * 运行时业务模型 -> 已发布流程名称 (flow_name) 映射
     * <p>
     * 由流程编译阶段登记，供弹屏等场景展示<b>真实</b>的流程名称，避免硬编码文案。
     * </p>
     */
    private final Map<String, String> flowNameByModel = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * 启动后自动自数据库加载当前所有已发布的最新流程版本配置
     */
    @PostConstruct
    public void loadPublishedFlowsOnStartup() {
        if (jdbcTemplate != null) {
            try {
                loadAllPublishedFlows();
            } catch (Exception e) {
                log.warn("⚠️ [FlowConfig] 启动加载 DB 线上流程异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 加载数据库中所有线上 PUBLISHED 状态的流程
     */
    public synchronized void loadAllPublishedFlows() {
        if (jdbcTemplate == null) return;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT f.flow_key, f.flow_name, f.model_type, v.version_no, v.definition_json " +
                    "FROM fcc_flow_definition f " +
                    "JOIN fcc_flow_definition_version v ON f.id = v.flow_definition_id " +
                    "WHERE v.publish_status = 'PUBLISHED'");

            for (Map<String, Object> row : rows) {
                String flowKey = (String) row.get("flow_key");
                String modelType = (String) row.get("model_type");
                String flowName = (String) row.get("flow_name");
                String json = (String) row.get("definition_json");
                compileAndApplyFlow(flowKey, modelType, flowName, json);
            }
            log.info("✅ [FlowConfig] 成功自数据库加载并编译 {} 条已发布话务流程", rows.size());
        } catch (Exception e) {
            log.warn("⚠️ [FlowConfig] 加载已发布流程失败: {}", e.getMessage());
        }
    }

    /**
     * 动态热重载指定流程
     *
     * @param flowKey 流程唯一键 (如 FLOW-INBOUND)
     * @return 是否重载成功
     */
    public synchronized boolean reloadFlow(String flowKey) {
        if (jdbcTemplate == null || flowKey == null) return false;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT f.flow_key, f.flow_name, f.model_type, v.version_no, v.definition_json " +
                    "FROM fcc_flow_definition f " +
                    "JOIN fcc_flow_definition_version v ON f.id = v.flow_definition_id " +
                    "WHERE f.flow_key = ? AND v.publish_status = 'PUBLISHED' " +
                    "ORDER BY v.version_no DESC LIMIT 1", flowKey);

            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.getFirst();
                String modelType = (String) row.get("model_type");
                String flowName = (String) row.get("flow_name");
                String json = (String) row.get("definition_json");
                compileAndApplyFlow(flowKey, modelType, flowName, json);
                log.info("🔄 [FlowConfig] 动态重载流程生效: flowKey={}, modelType={}", flowKey, modelType);
                return true;
            } else {
                log.warn("⚠️ [FlowConfig] 未找到对应已发布版本: flowKey={}", flowKey);
            }
        } catch (Exception e) {
            log.error("❌ [FlowConfig] 动态重载流程异常: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * 将编排配置 JSON 动态编译为运行时 FlowNode 状态机执行链
     *
     * @param flowKey        流程唯一键 (如 FLOW-INBOUND)
     * @param modelType      流程归属业务模型
     * @param flowName       流程展示名称 (落库事实，供弹屏展示)
     * @param definitionJson 流程定义 JSON
     */
    private void compileAndApplyFlow(String flowKey, String modelType, String flowName, String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) throw new IllegalArgumentException("流程定义不能为空");
        if (objectMapper == null) objectMapper = new ObjectMapper();

        try {
            JsonNode root = com.chandler.fcc.common.protocol.FlowDefinitionValidator.validate(definitionJson);
            String routeMode = root.path("routeMode").asText();
            String targetModelKey = modelType != null ? modelType : "INBOUND";
            if ("FLOW-INBOUND".equalsIgnoreCase(flowKey) || "INBOUND".equalsIgnoreCase(targetModelKey)) {
                targetModelKey = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
            } else if ("FLOW-OUTBOUND".equalsIgnoreCase(flowKey) || "OUTBOUND".equalsIgnoreCase(targetModelKey)) {
                targetModelKey = FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
            }

            // 动态编译 ROUTE 阶段动作
            List<FlowNode> routeNodes = new ArrayList<>();
            Map<String, String> routeData = new HashMap<>();
            routeData.put("routeMode", routeMode);

            String workNo = root.path("didDirectConfig").path("workNo").asText();
            routeData.put("workNo", workNo);
            routeNodes.add(FlowNode.builder()
                    .modelKey(targetModelKey)
                    .modelType(FlowModelType.valueOf(targetModelKey))
                    .stageState(CallStageState.ROUTE)
                    .actionKey("did-direct-dial-agent")
                    .actionType(ActionType.DIAL_AGENT)
                    .order(1)
                    .data(new HashMap<>(routeData))
                    .build());

            // 覆盖或更新运行时 flowMap
            flowMap.put(targetModelKey + ":" + CallStageState.ROUTE.name(), routeNodes);
            if (flowName != null && !flowName.isBlank()) {
                flowNameByModel.put(targetModelKey, flowName);
            }
            log.info("🎯 [FlowConfig 编译完成] 流程 {} 成功编译 ROUTE 阶段节点, 路由模式: {}, 节点数: {}",
                    flowKey, routeMode, routeNodes.size());

        } catch (Exception e) {
            throw new IllegalArgumentException("流程编译失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据阶段与业务模型获取流程动作节点链
     *
     * @param stage    呼叫生命周期阶段
     * @param modelKey 业务流转模式标识
     * @return 步骤节点列表
     */
    public List<FlowNode> getFlowNodes(CallStageState stage, String modelKey) {
        if (modelKey == null) {
            modelKey = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
        }
        String key = modelKey + ":" + stage.name();
        return flowMap.getOrDefault(key, Collections.emptyList());
    }

    /**
     * 获取某业务模型当前生效的流程名称
     * <p>
     * 名称来源于 {@code fcc_flow_definition.flow_name} 的事实落库值，
     * 未加载到已发布流程时返回空，调用方应留空而非填充占位文案。
     * </p>
     *
     * @param modelKey 业务模型标识
     * @return 流程名称；未知时返回空
     */
    public java.util.Optional<String> getFlowName(String modelKey) {
        if (modelKey == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(flowNameByModel.get(modelKey));
    }
}
