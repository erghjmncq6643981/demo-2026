package com.chandler.fcc.server.flow.action;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowCtrlRecord;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作总控调度管理器
 * <p>
 * 统一调度 FCC 动作执行器（IFccAction），根据流程节点的 {@link ActionType}
 * 路由分发至具体执行器，并全程持久化动作流转审计记录（FlowCtrlRecord）。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultActionExecutorsManager {

    private final List<AbstractFccActionExecutor> handlers;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内存存储通话动作执行轨迹流水（可供管理端与测试查询）
     */
    private final Map<String, List<FlowCtrlRecord>> callFlowRecords = new ConcurrentHashMap<>();

    /**
     * 调度执行指定流程节点的动作，自动注入通话上下文属性
     *
     * @param callInfo 通话聚合根业务上下文
     * @param flowNode 待执行流程节点
     */
    public void publish(CallInfoBO callInfo, FlowNode flowNode) {
        if (callInfo == null) {
            publish("", flowNode);
            return;
        }

        Map<String, Object> mergedData = new HashMap<>();
        if (flowNode.getData() != null) {
            mergedData.putAll(flowNode.getData());
        }
        if (callInfo.getData() != null) {
            mergedData.putAll(callInfo.getData());
        }
        if (callInfo.getCtrlId() != null) {
            mergedData.putIfAbsent("ctrlUuid", callInfo.getCtrlId());
        }
        if (callInfo.getCallId() != null) {
            mergedData.putIfAbsent("callUuid", callInfo.getCallId());
        }
        if (callInfo.getCallerNumber() != null) {
            mergedData.putIfAbsent("callerNumber", callInfo.getCallerNumber());
        }
        if (callInfo.getDestinationNumber() != null) {
            mergedData.putIfAbsent("destNumber", callInfo.getDestinationNumber());
        }
        if (callInfo.getAgentChannelUuid() != null) {
            mergedData.putIfAbsent("agentChannelUuid", callInfo.getAgentChannelUuid());
            mergedData.putIfAbsent("uuidB", callInfo.getAgentChannelUuid());
        }
        if (callInfo.getGuestChannelUuid() != null) {
            mergedData.putIfAbsent("guestChannelUuid", callInfo.getGuestChannelUuid());
            mergedData.putIfAbsent("uuidA", callInfo.getGuestChannelUuid());
        }
        if (callInfo.getAgentExt() != null) {
            mergedData.putIfAbsent("agentExt", callInfo.getAgentExt());
        }

        FlowNode execNode = FlowNode.builder()
                .modelKey(flowNode.getModelKey() != null ? flowNode.getModelKey() : callInfo.getModelKey())
                .modelType(flowNode.getModelType())
                .stageState(flowNode.getStageState() != null ? flowNode.getStageState() : callInfo.getStageState())
                .actionKey(flowNode.getActionKey())
                .actionType(flowNode.getActionType())
                .order(flowNode.getOrder())
                .data(mergedData)
                .build();

        publish(callInfo.getCallId() != null ? callInfo.getCallId() : callInfo.getCtrlId(), execNode);
    }

    /**
     * 调度执行指定流程节点的动作
     *
     * @param callId   通话唯一标识
     * @param flowNode 流程节点
     */
    public void publish(String callId, FlowNode flowNode) {
        log.info("⚡ [动作调度] CallID: {}, ActionType: {}, StepKey: {}",
                callId, flowNode.getActionType(), flowNode.getActionKey());

        Optional<AbstractFccActionExecutor> handler = handlers.stream()
                .filter(h -> h.getActionType().equals(flowNode.getActionType()))
                .findFirst();

        if (handler.isEmpty()) {
            log.warn("⚠️ [动作未注册] 找不到对应的 FCC 动作执行器: {}", flowNode.getActionType());
            return;
        }

        String flowInstanceId = recordFlowStep(callId, flowNode);
        try {
            handler.get().execute(callId, flowInstanceId, flowNode);
        } catch (Exception e) {
            log.error("❌ [动作执行异常] CallID: {}, Action: {}, Error: {}",
                    callId, flowNode.getActionType(), e.getMessage(), e);
        }
    }

    /**
     * 记录流程步骤审计记录
     *
     * @param callId   业务通话标识
     * @param flowNode 流程节点
     * @return 步骤执行唯一标识
     */
    private String recordFlowStep(String callId, FlowNode flowNode) {
        String flowUuid = IdUtil.getUuid();
        String safeKey = callId != null ? callId : "";
        List<FlowCtrlRecord> records = callFlowRecords.computeIfAbsent(safeKey, k -> new ArrayList<>());

        String detailJson = "";
        try {
            detailJson = objectMapper.writeValueAsString(flowNode.getData());
        } catch (Exception ignored) {}

        FlowCtrlRecord record = FlowCtrlRecord.builder()
                .flowInstanceId(flowUuid)
                .callId(safeKey)
                .stepKey(flowNode.getActionKey())
                .stepType(flowNode.getActionType() != null ? flowNode.getActionType().name() : "")
                .stepOrder(records.size() + 1)
                .modelKey(flowNode.getModelKey())
                .stageState(flowNode.getStageState() != null ? flowNode.getStageState().name() : "")
                .detail(detailJson)
                .createTime(LocalDateTime.now())
                .build();

        records.add(record);
        log.debug("📝 [动作轨迹落盘] 步骤 #{}: {}", record.getStepOrder(), record.getStepType());
        return flowUuid;
    }

    /**
     * 快速记录单条自定义业务审计轨迹
     *
     * @param callInfo   通话上下文
     * @param stepKey    步骤标识
     * @param actionType 动作类型名称
     * @param data       明细参数
     */
    public void recordAudit(CallInfoBO callInfo, String stepKey, String actionType, Map<String, ?> data) {
        String callId = callInfo != null ? (callInfo.getCallId() != null ? callInfo.getCallId() : callInfo.getCtrlId()) : "";
        Map<String, Object> convertedData = new HashMap<>();
        if (data != null) {
            convertedData.putAll(data);
        }
        FlowNode node = FlowNode.builder()
                .actionKey(stepKey)
                .actionType(ActionType.valueOfAction(actionType))
                .modelKey(callInfo != null ? callInfo.getModelKey() : "")
                .stageState(callInfo != null ? callInfo.getStageState() : null)
                .data(convertedData)
                .build();
        recordFlowStep(callId, node);
    }

    /**
     * 查询指定通话的所有流转审计流水
     *
     * @param callId 业务通话标识
     * @return 审计记录列表
     */
    public List<FlowCtrlRecord> getRecords(String callId) {
        if (callId == null) {
            return Collections.emptyList();
        }
        return callFlowRecords.getOrDefault(callId, Collections.emptyList());
    }
}
