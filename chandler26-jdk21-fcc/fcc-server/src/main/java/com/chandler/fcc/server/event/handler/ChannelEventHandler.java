package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.FccEventMethods;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.application.PhoneBindingService;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将通道生命周期事件关联到业务通话，并交给固定运行模型处理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChannelEventHandler implements FccEventHandler {

    private static final String STATE_START = "START";
    private static final String STATE_DESTROY = "DESTROY";
    private static final String DIRECTION_INBOUND = "inbound";

    private final CallSessionManager sessions;
    private final PhoneBindingService phoneBinding;
    private final OutboundCallService outboundCalls;
    private final InboundCallService inboundCalls;

    /**
     * 判断是否为通道生命周期事件。
     *
     * @param method 标准事件方法名
     * @return 是否支持
     */
    @Override
    public boolean supports(String method) {
        return FccEventMethods.CHANNEL.equalsIgnoreCase(method);
    }

    /**
     * 关联业务通话并交给话机绑定、呼出或呼入固定模型处理。
     *
     * @param params 通道事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String nodeId = text(params, "node_id");
        String state = text(params, "state");
        String channelUuid = text(params, "uuid");
        String controlId = text(params, "ctrl_uuid");
        if (nodeId == null || state == null || channelUuid == null) {
            throw new IllegalArgumentException("通道事件缺少 node_id、state 或 uuid");
        }

        CallInfoBO call = sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .orElse(null);
        if (call == null) {
            call = createInboundCall(params, nodeId, state, channelUuid, controlId);
            if (call == null) return;
        } else {
            refreshCallFacts(call, params, nodeId);
        }

        call.putData("flowEventId", params.path("event_id").asText());
        call.putData("flowSourceTime", params.path("timestamp").asLong());

        if (phoneBinding.channel(call, params)) {
            if (STATE_DESTROY.equals(state)) sessions.removeSession(call.getCtrlId());
            return;
        }
        if (outboundCalls.event(call, params)) return;
        if (inboundCalls.event(call, params)) return;

        log.warn(
            "[话务事件] 通话没有匹配固定运行模型 callId={} model={} state={}",
            call.getCallId(),
            call.getModelKey(),
            state
        );
    }

    /**
     * 为首次呼入 START 事件创建业务通话上下文。
     *
     * @param params 通道事件参数
     * @param nodeId 节点标识
     * @param state 通道状态
     * @param channelUuid 客户话道标识
     * @param controlId 控制标识，可为空
     * @return 新建通话；非首次呼入事件返回空
     */
    private CallInfoBO createInboundCall(
        JsonNode params,
        String nodeId,
        String state,
        String channelUuid,
        String controlId
    ) {
        String direction = text(params, "direction");
        if (!STATE_START.equals(state) || !DIRECTION_INBOUND.equalsIgnoreCase(direction)) {
            log.warn(
                "[话务事件] 未关联的话道事件待对账 nodeId={} channelUuid={} state={}",
                nodeId,
                channelUuid,
                state
            );
            return null;
        }

        String effectiveControlId = controlId == null || controlId.isBlank()
            ? IdUtil.getCtrlId("inbound")
            : controlId;
        CallInfoBO call = CallInfoBO.builder()
            .nodeId(nodeId)
            .callId(IdUtil.getCallId())
            .ctrlId(effectiveControlId)
            .guestChannelUuid(channelUuid)
            .agentChannelUuid(text(params, "peer_uuid"))
            .modelKey(FlowModelType.INBOUND_CUSTOMER_SERVICE.name())
            .direction(DirectionType.INBOUND)
            .callerNumber(text(params, "cid_number"))
            .destinationNumber(text(params, "dest_number"))
            .duration(integer(params, "duration"))
            .billsec(integer(params, "billsec"))
            .hangupCause(text(params, "cause"))
            .data(new HashMap<>())
            .build();
        call.putData("ctrlId", effectiveControlId);
        call.putData("callId", call.getCallId());
        call.putData("guestChannelUuid", channelUuid);
        sessions.registerSession(call);
        sessions.bindChannel(channelUuid, effectiveControlId);
        return call;
    }

    /**
     * 将事件中的权威通道事实合并到既有通话上下文。
     *
     * @param call 既有通话
     * @param params 通道事件参数
     * @param nodeId 节点标识
     */
    private void refreshCallFacts(CallInfoBO call, JsonNode params, String nodeId) {
        if (call.getNodeId() != null && !nodeId.equals(call.getNodeId())) {
            throw new IllegalArgumentException("通道节点与通话归属不一致");
        }
        call.setNodeId(nodeId);
        Integer duration = integer(params, "duration");
        Integer billsec = integer(params, "billsec");
        if (duration != null) call.setDuration(duration);
        if (billsec != null) call.setBillsec(billsec);
        String cause = text(params, "cause");
        if (cause != null) call.setHangupCause(cause);
        String peerUuid = text(params, "peer_uuid");
        if (peerUuid != null && call.getAgentChannelUuid() == null) {
            call.setAgentChannelUuid(peerUuid);
        }
    }

    /**
     * 读取可选文本字段。
     *
     * @param node JSON 节点
     * @param field 字段名
     * @return 文本值，缺失时为空
     */
    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /**
     * 读取可选整数字段。
     *
     * @param node JSON 节点
     * @param field 字段名
     * @return 整数值，缺失时为空
     */
    private Integer integer(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }
}
