package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.application.PhoneBindingService;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.telephony.application.CallTransferService;
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

    private static final String DIRECTION_INBOUND = "inbound";

    private final CallSessionManager sessions;
    private final PhoneBindingService phoneBinding;
    private final OutboundCallService outboundCalls;
    private final InboundCallService inboundCalls;
    private final CallTransferService transferService;

    /**
     * 判断是否为通道生命周期事件。
     *
     * @param method 标准事件方法
     * @return 是否支持
     */
    @Override
    public boolean supports(FccEventMethod method) {
        return method == FccEventMethod.CHANNEL;
    }

    /**
     * 关联业务通话并交给话机绑定、呼出或呼入固定模型处理。
     *
     * @param params 通道事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String nodeId = text(params, FccEventField.NODE_ID);
        String stateValue = text(params, FccEventField.STATE);
        String channelUuid = text(params, FccEventField.CHANNEL_UUID);
        String controlId = text(params, FccEventField.CONTROL_ID);
        ChannelEventState state = stateValue == null ? null : ChannelEventState.fromWireValue(stateValue);
        if (nodeId == null || state == null || channelUuid == null) {
            throw new IllegalArgumentException("通道事件缺少 node_id、state 或 uuid");
        }

        CallInfoBO call = sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .orElse(null);
        if (call == null) {
            if (outboundCalls.isAgentOriginatedEntry(params, state)) {
                call = outboundCalls.createAgentOriginated(
                    params,
                    nodeId,
                    state,
                    channelUuid,
                    controlId
                );
                if (call == null) {
                    return;
                }
            } else {
                call = createInboundCall(params, nodeId, state, channelUuid, controlId);
            }
            if (call == null) return;
        } else {
            refreshCallFacts(call, params, nodeId);
        }

        call.putData("flowEventId", params.path(FccEventField.EVENT_ID.getWireName()).asText());
        call.putData("flowSourceTime", params.path(FccEventField.SOURCE_TIMESTAMP.getWireName()).asLong());

        if (phoneBinding.channel(call, params)) {
            if (state == ChannelEventState.DESTROY) sessions.removeSession(call.getCtrlId());
            return;
        }
        if (transferService.handleChannelEvent(call, params, state, channelUuid)) return;
        if (outboundCalls.event(call, params)) return;
        if (inboundCalls.event(call, params)) return;

        log.warn(
            "[话务事件] 通话没有匹配固定运行模型 callId={} model={} state={}",
            call.getCallId(),
            call.getModelKey(),
            state.getWireValue()
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
        ChannelEventState state,
        String channelUuid,
        String controlId
    ) {
        String direction = text(params, FccEventField.DIRECTION);
        if (state != ChannelEventState.START || !DIRECTION_INBOUND.equalsIgnoreCase(direction)) {
            log.warn(
                "[话务事件] 未关联的话道事件待对账 nodeId={} channelUuid={} state={}",
                nodeId,
                channelUuid,
                state.getWireValue()
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
            .agentChannelUuid(text(params, FccEventField.PEER_CHANNEL_UUID))
            .modelKey(FlowModelType.INBOUND_CUSTOMER_SERVICE.name())
            .direction(DirectionType.INBOUND)
            .callerNumber(text(params, FccEventField.CALLER_NUMBER))
            .destinationNumber(text(params, FccEventField.DESTINATION_NUMBER))
            .duration(integer(params, FccEventField.DURATION))
            .billsec(integer(params, FccEventField.BILL_SECONDS))
            .hangupCause(text(params, FccEventField.CAUSE))
            .data(new HashMap<>())
            .build();
        call.putData("ctrlId", effectiveControlId);
        call.putData("callId", call.getCallId());
        call.putData("guestChannelUuid", channelUuid);
        call.putData("routingContext", text(params, FccEventField.CONTEXT));
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
        Integer duration = integer(params, FccEventField.DURATION);
        Integer billsec = integer(params, FccEventField.BILL_SECONDS);
        if (duration != null) call.setDuration(duration);
        if (billsec != null) call.setBillsec(billsec);
        String cause = text(params, FccEventField.CAUSE);
        if (cause != null) call.setHangupCause(cause);
        String peerUuid = text(params, FccEventField.PEER_CHANNEL_UUID);
        if (peerUuid != null && call.getAgentChannelUuid() == null) {
            call.setAgentChannelUuid(peerUuid);
        }
    }

    /**
     * 读取可选文本字段。
     *
     * @param node JSON 节点
     * @param field 规范字段
     * @return 文本值，缺失时为空
     */
    private String text(JsonNode node, FccEventField field) {
        return node.hasNonNull(field.getWireName()) ? node.get(field.getWireName()).asText() : null;
    }

    /**
     * 读取可选整数字段。
     *
     * @param node JSON 节点
     * @param field 规范字段
     * @return 整数值，缺失时为空
     */
    private Integer integer(JsonNode node, FccEventField field) {
        return node.hasNonNull(field.getWireName()) ? node.get(field.getWireName()).asInt() : null;
    }
}
