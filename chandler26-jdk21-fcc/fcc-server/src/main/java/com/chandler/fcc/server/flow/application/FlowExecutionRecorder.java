package com.chandler.fcc.server.flow.application;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.protocol.StagedFlowDefinition;
import com.chandler.fcc.common.protocol.SystemFlowModels;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.flow.infrastructure.FlowExecutionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每通电话固定流程快照；阶段迁移与 Call 保存处于同一事务，不将 RPC 应答当完成。
 */
@Service
@RequiredArgsConstructor
public class FlowExecutionRecorder {

    private final FlowExecutionMapper mapper;
    private final FlowConfig flowConfig;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 同步已保存通话的阶段事实，重复事件不会追加重复尝试。
     *
     * @param call 已验证身份的通话上下文
     * @throws IllegalStateException 缺失部署模板或快照损坏
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(CallInfoBO call) {
        String template = call.getDataStr("runtimeTemplate", "");
        if (!Set.of("INBOUND", "AGENT_FIRST", "AGENT_ORIGINATED", "NOTIFICATION", "PHONE_BINDING").contains(template)) return;
        try {
            var existing = mapper.lock(call.getCallId());
            Map<String, Object> row = new HashMap<>();
            row.put("call", call.getCallId());
            if (existing == null) {
                var definition = flowConfig.getPublishedFlow(
                    call.getDataStr("flowVersionId", null),
                    template
                ).orElseThrow(() -> new IllegalStateException(
                    "缺少数据库固定流程模板，请执行阶段流程迁移"
                ));
                row.put("flowKey", definition.flowKey());
                row.put("versionNo", definition.versionNo());
                row.put("definitionId", definition.definitionId());
                row.put("versionId", definition.versionId());
                var snapshot = json.readTree(definition.definitionJson());
                if (!snapshot.has("nodes")) {
                    var fullModel = (ObjectNode) StagedFlowDefinition.template(template);
                    fullModel.setAll((ObjectNode) snapshot);
                    snapshot = fullModel;
                }
                row.put(
                    "snapshot",
                    json.writeValueAsString(
                        Map.of(
                            "definition",
                            snapshot,
                            "flowKey",
                            definition.flowKey(),
                            "versionNo",
                            definition.versionNo()
                        )
                    )
                );
                mapper.create(row);
                existing = mapper.lock(call.getCallId());
            }
            if ("ENDED".equals(existing.get("status"))) return;
            String step = stage(call, template);
            String token =
                step +
                ":" +
                call.getDataStr("ivrAttempt", "0") +
                ":" +
                ("ROUTE".equals(step) ? call.getAgentChannelUuid() : "");
            var context = json.readTree(existing.get("context").toString());
            if (token.equals(context.path("executionToken").asText())) return;
            row.put("step", step);
            row.put("action", SystemFlowModels.action(template, step).name());
            row.put("token", token);
            row.put("id", IdUtil.nextId());
            row.put("event", call.getDataStr("flowEventId", null));
            row.put("command", call.getDataStr("flowCommandId", null));
            row.put("status", "END".equals(step) ? "SUCCEEDED" : "WAITING");
            String previous = String.valueOf(existing.get("step"));
            row.put(
                "previousStatus",
                "END".equals(step) && !Set.of("CONNECTED", "CONFIRM").contains(previous)
                    ? "INTERRUPTED"
                    : "SUCCEEDED"
            );
            var facts = new HashMap<String, Object>();
            for (String key : List.of(
                "flowBranch",
                "directOwner",
                "groupCode",
                "notificationConfirmed",
                "flowSourceTime",
                "flowCommandId"
            ))
                if (call.getData().containsKey(key)) facts.put(key, call.getData().get(key));
            if (call.getHangupCause() != null) facts.put("cause", call.getHangupCause());
            row.put("input", json.writeValueAsString(facts));
            row.put("output", row.get("input"));
            mapper.finish(row);
            mapper.append(row);
            mapper.advance(row);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("流程执行快照损坏", e);
        }
    }

    /**
     * 根据权威事件及持久运行状态识别固定阶段。
     *
     * @param call 通话
     * @param template 模板
     * @return 阶段键
     */
    private String stage(CallInfoBO call, String template) {
        if (call.getData().containsKey("terminal")) return "END";
        if ("PHONE_BINDING".equals(template)) {
            if (call.getData().containsKey("bindingCompleted")) return "VERIFY_BINDING";
            return call.getData().containsKey("bindingPromptSent") ? "COLLECT_CODE" : "ENTRY";
        }
        if ("NOTIFICATION".equals(template)) {
            if (Boolean.TRUE.equals(call.getData().get("notificationConfirmed"))) return "CONFIRM";
            return call.getData().containsKey("notificationStarted") ? "NOTIFY" : "DIAL_CUSTOMER";
        }
        if (call.getStageState() == CallStageState.CONNECTED) return "CONNECTED";
        if (call.getData().containsKey("bridgeRequested")) return "BRIDGE";
        if ("AGENT_FIRST".equals(template)) return call.getData().containsKey("agentReady")
            ? "DIAL_CUSTOMER"
            : "DIAL_AGENT";
        if ("AGENT_ORIGINATED".equals(template)) return call.getData().containsKey("agentReady")
            ? "DIAL_CUSTOMER"
            : "ENTRY";
        if (call.getData().containsKey("ivrWaiting")) return "MENU";
        if (call.getData().containsKey("ivrBranchPending")) return "BRANCH";
        return call.getData().containsKey("guestReady") ? "ROUTE" : "ENTRY";
    }
}
