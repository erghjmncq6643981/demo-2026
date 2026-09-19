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
 * 话务流程编排规则库（支持静态预置基线与动态 DB/JSON 编译热加载）
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
     * 初始化默认流程模型配置
     */
    public FlowConfig() {
        initDefaultFlows();
    }

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
        if (definitionJson == null || definitionJson.isBlank()) return;
        if (objectMapper == null) objectMapper = new ObjectMapper();

        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            String routeMode = root.hasNonNull("routeMode") ? root.get("routeMode").asText() : "HTTP_CALLBACK";
            String targetModelKey = modelType != null ? modelType : "INBOUND";
            if ("FLOW-INBOUND".equalsIgnoreCase(flowKey) || "INBOUND".equalsIgnoreCase(targetModelKey)) {
                targetModelKey = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();
            } else if ("FLOW-OUTBOUND".equalsIgnoreCase(flowKey) || "OUTBOUND".equalsIgnoreCase(targetModelKey)) {
                targetModelKey = FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
            }

            // 动态编译 ROUTE 阶段动作
            List<FlowNode> routeNodes = new ArrayList<>();

            if ("DID_DIRECT".equalsIgnoreCase(routeMode)) {
                JsonNode didNode = root.get("didDirectConfig");
                String agentId = (didNode != null && didNode.hasNonNull("agentId")) ? didNode.get("agentId").asText() : "1007";
                String agentExt = ("901001".equals(agentId) || "1001".equals(agentId)) ? "1001" : ("1007".equals(agentId) ? "1007" : agentId);
                routeNodes.add(FlowNode.builder()
                        .modelKey(targetModelKey)
                        .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                        .stageState(CallStageState.ROUTE)
                        .actionKey("did-direct-dial-" + agentExt)
                        .actionType(ActionType.DIAL_AGENT)
                        .order(1)
                        .data(new HashMap<>(Map.of("agentExt", agentExt, "routeMode", "DID_DIRECT", "agentId", agentId)))
                        .build());
            } else if ("RULE_ENGINE".equalsIgnoreCase(routeMode)) {
                routeNodes.add(FlowNode.builder()
                        .modelKey(targetModelKey)
                        .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                        .stageState(CallStageState.ROUTE)
                        .actionKey("rule-engine-dial-1007")
                        .actionType(ActionType.DIAL_AGENT)
                        .order(1)
                        .data(new HashMap<>(Map.of("agentExt", "1007", "routeMode", "RULE_ENGINE")))
                        .build());
            } else {
                // HTTP_CALLBACK
                routeNodes.add(FlowNode.builder()
                        .modelKey(targetModelKey)
                        .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                        .stageState(CallStageState.ROUTE)
                        .actionKey("http-callback-dial-1007")
                        .actionType(ActionType.DIAL_AGENT)
                        .order(1)
                        .data(new HashMap<>(Map.of("agentExt", "1007", "routeMode", "HTTP_CALLBACK")))
                        .build());
            }

            // 覆盖或更新运行时 flowMap
            flowMap.put(targetModelKey + ":" + CallStageState.ROUTE.name(), routeNodes);
            if (flowName != null && !flowName.isBlank()) {
                flowNameByModel.put(targetModelKey, flowName);
            }
            log.info("🎯 [FlowConfig 编译完成] 流程 {} 成功编译 ROUTE 阶段节点, 路由模式: {}, 节点数: {}",
                    flowKey, routeMode, routeNodes.size());

        } catch (Exception e) {
            log.warn("⚠️ [FlowConfig] 编译流程 JSON 失败: {}", e.getMessage());
        }
    }

    private void initDefaultFlows() {
        String inboundModel = FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        // 1. 来电呼入流程 (INBOUND_CUSTOMER_SERVICE):
        // START 阶段: 播报 IVR 导航语音并收号 (驻留等待按键)
        addNode(inboundModel, CallStageState.START, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.START)
                .actionKey("ivr-navigation")
                .actionType(ActionType.READ_DTMF)
                .order(1)
                .data(new HashMap<>(Map.of(
                        "soundFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_navigation.wav",
                        "regex", "[1-3]",
                        "actionAfter", "park",
                        "prompt", "您好，欢迎致电客服热线。人工服务请按1，业务咨询请按2，投诉建议请按3。"
                )))
                .build());

        // ROUTE 阶段 (按键路由): 呼叫坐席 1007
        addNode(inboundModel, CallStageState.ROUTE, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.ROUTE)
                .actionKey("dial-agent-1007")
                .actionType(ActionType.DIAL_AGENT)
                .order(1)
                .data(new HashMap<>(Map.of("agentExt", "1007")))
                .build());

        // CONNECTED 阶段 (双方接通): 开启录音
        addNode(inboundModel, CallStageState.CONNECTED, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.CONNECTED)
                .actionKey("start-record-inbound")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(new HashMap<>(Map.of("action", "START")))
                .build());

        // NORMAL_END 阶段: 停止录音
        addNode(inboundModel, CallStageState.NORMAL_END, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.NORMAL_END)
                .actionKey("stop-record")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(new HashMap<>(Map.of("action", "STOP")))
                .build());

        // NORMAL_END 阶段: 坐席挂机后对未挂机客户播放满意度评价并收号落库
        addNode(inboundModel, CallStageState.NORMAL_END, FlowNode.builder()
                .modelKey(inboundModel)
                .modelType(FlowModelType.INBOUND_CUSTOMER_SERVICE)
                .stageState(CallStageState.NORMAL_END)
                .actionKey("satisfaction-survey")
                .actionType(ActionType.READ_DTMF)
                .order(2)
                .data(new HashMap<>(Map.of(
                        "soundFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_evaluation_prompt.wav",
                        "thankYouFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_thankyou.wav",
                        "regex", "[1-5]",
                        "tries", "2",
                        "timeout", "5000",
                        "actionAfter", "hangup",
                        "prompt", "请对本次服务进行评价，非常满意请按1，满意请按2，不满意请按3。"
                )))
                .build());

        // 2. 双向外呼流程 (OUTBOUND_TWO_WAY_CALL):
        String outboundModel = FlowModelType.OUTBOUND_TWO_WAY_CALL.name();

        // START 阶段: 先呼叫坐席 1008
        addNode(outboundModel, CallStageState.START, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.START)
                .actionKey("dial-agent-first")
                .actionType(ActionType.DIAL_AGENT)
                .order(1)
                .data(new HashMap<>(Map.of("agentExt", "1008")))
                .build());

        // ROUTE 阶段 (坐席接听进入 Park): 呼叫客户 1007 并桥接
        addNode(outboundModel, CallStageState.ROUTE, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.ROUTE)
                .actionKey("dial-guest-second")
                .actionType(ActionType.DIAL_GUEST)
                .order(1)
                .data(new HashMap<>(Map.of("destNumber", "1007")))
                .build());

        // CONNECTED 阶段: 开启录音
        addNode(outboundModel, CallStageState.CONNECTED, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.CONNECTED)
                .actionKey("start-record-outbound")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(new HashMap<>(Map.of("action", "START")))
                .build());

        // NORMAL_END 阶段: 停止录音
        addNode(outboundModel, CallStageState.NORMAL_END, FlowNode.builder()
                .modelKey(outboundModel)
                .modelType(FlowModelType.OUTBOUND_TWO_WAY_CALL)
                .stageState(CallStageState.NORMAL_END)
                .actionKey("stop-record-outbound")
                .actionType(ActionType.RECORD)
                .order(1)
                .data(new HashMap<>(Map.of("action", "STOP")))
                .build());

        // 3. 自动外呼通知流程 (AUTO_DIAL_NOTIFICATION):
        String notifyModel = FlowModelType.AUTO_DIAL_NOTIFICATION.name();

        // START 阶段: 自动外呼目标客户
        addNode(notifyModel, CallStageState.START, FlowNode.builder()
                .modelKey(notifyModel)
                .modelType(FlowModelType.AUTO_DIAL_NOTIFICATION)
                .stageState(CallStageState.START)
                .actionKey("notify-dial-guest")
                .actionType(ActionType.DIAL_GUEST)
                .order(1)
                .data(new HashMap<>(Map.of(
                        "destNumber", "1008",
                        "callerNumber", "9000"
                )))
                .build());

        // ROUTE 阶段 (客户接听进入 Park): 播放通知语音并收号确认
        addNode(notifyModel, CallStageState.ROUTE, FlowNode.builder()
                .modelKey(notifyModel)
                .modelType(FlowModelType.AUTO_DIAL_NOTIFICATION)
                .stageState(CallStageState.ROUTE)
                .actionKey("notify-play-read")
                .actionType(ActionType.READ_DTMF)
                .order(1)
                .data(new HashMap<>(Map.of(
                        "soundFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_autodial.wav",
                        "thankYouFile", "/Users/chandler/Documents/repository/github/cloud-2025/chandler26-jdk17-freeswitch-FCC/sounds/ivr_thankyou.wav",
                        "regex", "[1-2]",
                        "tries", "2",
                        "timeout", "2000",
                        "actionAfter", "hangup",
                        "prompt", "您好，这里是售后服务中心。通知您，您申请的业务已受理成功。确认办理请按一，咨询详情请按二，稍后请留意手机短信。祝您生活愉快，再见！"
                )))
                .build());
    }

    private void addNode(String modelKey, CallStageState stage, FlowNode node) {
        String key = modelKey + ":" + stage.name();
        flowMap.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
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
