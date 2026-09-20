package com.chandler.fcc.server.flow.application;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.protocol.StagedFlowDefinition;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.flow.infrastructure.FlowExecutionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
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
        if (!java.util.Set.of("INBOUND", "AGENT_FIRST", "NOTIFICATION").contains(template)) return;
        try {
            var existing = mapper.lock(call.getCallId());
            Map<String, Object> row = new HashMap<>();
            row.put("call", call.getCallId());
            row.put("tenant", call.getData().get("tenantId"));
            if (existing == null) {
                var definition = mapper.definition(call.getDataStr("flowVersionId", null), template);
                if (definition == null) throw new IllegalStateException(
                    "缺少数据库固定流程模板，请执行阶段流程迁移"
                );
                row.putAll(definition);
                var snapshot = json.readTree(definition.get("definition").toString());
                if (!snapshot.has("nodes")) {
                    var fullModel =
                        (com.fasterxml.jackson.databind.node.ObjectNode) StagedFlowDefinition.template(
                            template
                        );
                    fullModel.setAll((com.fasterxml.jackson.databind.node.ObjectNode) snapshot);
                    snapshot = fullModel;
                }
                row.put(
                    "snapshot",
                    json.writeValueAsString(
                        Map.of(
                            "definition",
                            snapshot,
                            "flowKey",
                            definition.get("flowKey"),
                            "versionNo",
                            definition.get("versionNo")
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
            row.put("token", token);
            row.put("id", IdUtil.nextId());
            row.put("event", call.getDataStr("flowEventId", null));
            row.put("status", "END".equals(step) ? "SUCCEEDED" : "WAITING");
            String previous = String.valueOf(existing.get("step"));
            row.put(
                "previousStatus",
                "END".equals(step) && !java.util.Set.of("CONNECTED", "CONFIRM").contains(previous)
                    ? "INTERRUPTED"
                    : "SUCCEEDED"
            );
            var facts = new HashMap<String, Object>();
            for (String key : java.util.List.of(
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
        if ("NOTIFICATION".equals(template)) {
            if (Boolean.TRUE.equals(call.getData().get("notificationConfirmed"))) return "CONFIRM";
            return call.getData().containsKey("notificationStarted") ? "NOTIFY" : "DIAL_CUSTOMER";
        }
        if (
            call.getStageState() == com.chandler.fcc.common.enums.CallStageState.CONNECTED
        ) return "CONNECTED";
        if (call.getData().containsKey("bridgeRequested")) return "BRIDGE";
        if ("AGENT_FIRST".equals(template)) return call.getData().containsKey("agentReady")
            ? "DIAL_CUSTOMER"
            : "DIAL_AGENT";
        if (call.getData().containsKey("ivrWaiting")) return "MENU";
        if (call.getData().containsKey("ivrBranchPending")) return "BRANCH";
        return call.getData().containsKey("guestReady") ? "ROUTE" : "ENTRY";
    }
}
