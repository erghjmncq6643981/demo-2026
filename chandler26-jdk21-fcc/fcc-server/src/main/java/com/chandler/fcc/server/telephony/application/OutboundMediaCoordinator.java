package com.chandler.fcc.server.telephony.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.chandler.fcc.common.dto.command.FNodeBridgeDTO;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.server.agent.domain.AgentWorkStatus;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.recording.application.CallRecordingService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.TEMPLATE_AGENT_ORIGINATED;

/** 外呼话道事实与媒体连接协调；调用方持有会话锁，网络调用不置于数据库事务。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundMediaCoordinator {
    private final AgentRuntimeMapper agents;
    private final CallPersistenceService persistence;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;
    private final CallRecordingService recordings;
    private final AgentWebSocketService websocket;

    /**
     * 保存当前外呼话道事实。
     *
     * @param call 当前业务通话
     * @param channelUuid 话道标识
     * @param state 标准话道状态
     * @param cause 挂机原因，可为空
     */
    public void persistLeg(CallInfoBO call, String channelUuid, String state, String cause) {
        boolean agentLeg = channelUuid.equals(call.getAgentChannelUuid());
        boolean agentOriginated = TEMPLATE_AGENT_ORIGINATED.equals(
            call.getDataStr("runtimeTemplate", "")
        );
        persistence.saveOrUpdateLeg(
            CallLegEntity.builder()
                .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                .channelUuid(channelUuid)
                .nodeId(call.getNodeId() != null && !call.getNodeId().isBlank() ? call.getNodeId() : null)
                .roleType(agentLeg ? "AGENT" : "CUSTOMER")
                .direction(agentLeg && agentOriginated ? "INBOUND" : "OUTBOUND")
                .endpointType(
                    agentLeg
                        ? call.getDataStr("agentEndpointType", null)
                        : "CARRIER"
                )
                .endpointId(agentLeg ? call.getAgentExt() : null)
                .metadata(agentLeg
                    ? JsonNodeFactory.instance.objectNode().put("agentWorkNo", call.getAgentWorkNo()).toString() : null)
                .callerNumber(agentLeg && agentOriginated ? call.getAgentExt() : call.getCallerNumber())
                .destinationNumber(call.getDestinationNumber())
                .routingContext(
                    agentLeg
                        ? call.getDataStr("routingContext", "default")
                        : call.getDataStr("guestContext", null)
                )
                .state(state)
                .hangupCause(cause)
                .endedAt(
                    ChannelEventState.DESTROY.getWireValue().equals(state)
                        ? LocalDateTime.now(ZoneOffset.UTC)
                        : null
                )
                .build()
        );
    }

    /**
     * 坐席话道就绪后持久化进度并呼叫客户。
     *
     * @param call 当前坐席先接听通话
     */
    public void startCustomerLeg(CallInfoBO call) {
        if (call.getData().putIfAbsent("agentReady", true) != null) {
            return;
        }
        try {
            client.nativeAPI("uuid_answer", call.getAgentChannelUuid());
        } catch (Exception ignored) {}
        persistence.saveOrUpdateSession(call);
        dial(call, false);
    }

    /**
     * 双方话道就绪后下发桥接指令。
     *
     * @param call 当前坐席先接听通话
     */
    public void bridgeAgentAndCustomer(CallInfoBO call) {
        if (
            !call.getData().containsKey("agentReady") ||
            call.getData().putIfAbsent("bridgeRequested", true) != null
        ) {
            return;
        }
        try {
            client.nativeAPI("uuid_answer", call.getGuestChannelUuid());
        } catch (Exception ignored) {}
        persistence.saveOrUpdateSession(call);
        flowActions.executeFNode(
            call,
            FlowActionType.CHANNEL_BRIDGE,
            FNodeBridgeDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getAgentChannelUuid())
                .peerUuid(call.getGuestChannelUuid())
                .build(),
            "bridge-" + call.getCallId()
        );
    }

    /**
     * 将真实桥接事件反映到通话事实和坐席客户端。
     *
     * @param call 当前通话
     */
    public void markConnected(CallInfoBO call) {
        recordings.start(call);
        flowActions.executeInternal(
            call,
            FlowActionType.WAIT_FOR_HANGUP,
            () -> {
                call.setStageState(CallStageState.CONNECTED);
                persistence.saveOrUpdateSession(call);
                if (call.getAgentWorkNo() != null) {
                    websocket.pushCallAnswered(
                        call.getAgentWorkNo(),
                        call.getCallId(),
                        Map.of("callId", call.getCallId())
                    );
                }
                return true;
            }
        );
    }

    /**
     * 使用坐席话道事件推进人工外呼工作状态，自动外呼不会进入本分支。
     *
     * @param call 当前通话
     * @param eventState 标准话道事件状态
     * @param channelUuid 事件话道标识
     */
    public void updateAgentWorkStatus(
        CallInfoBO call,
        ChannelEventState eventState,
        String channelUuid
    ) {
        if (
            call.getAgentWorkNo() == null ||
            !channelUuid.equals(call.getAgentChannelUuid())
        ) {
            return;
        }
        AgentWorkStatus status = switch (eventState) {
            case CALLING -> AgentWorkStatus.CALLING;
            case RINGING -> AgentWorkStatus.RINGING;
            case ANSWERED, READY, BRIDGE -> AgentWorkStatus.ANSWERED;
            default -> null;
        };
        if (status != null) {
            agents.updateCallStatus(call.getAgentWorkNo(), call.getCallId(), status.name());
        }
    }

    /**
     * 使用预分配话道 UUID 发起坐席或客户侧拨号。
     *
     * @param call 当前通话
     * @param agentSide 是否呼叫坐席侧
     */
    public void dial(CallInfoBO call, boolean agentSide) {
        String channelUuid = agentSide
            ? call.getAgentChannelUuid()
            : call.getGuestChannelUuid();
        String destination = agentSide
            ? call.getAgentExt()
            : call.getDataStr("guestNumber", "");
        String context = agentSide ? "default" : call.getDataStr("guestContext", "");
        FNodeDialDTO command = FNodeDialDTO.builder()
            .ctrlUuid(call.getCtrlId())
            .uuid(channelUuid)
            .timeout(30)
            .destination(
                FNodeDialDTO.Destination.builder()
                    .callParams(
                        List.of(
                            FNodeDialDTO.CallParam.builder()
                                .uuid(channelUuid)
                                .dialString(destination)
                                .context(context)
                                .cidNumber(call.getCallerNumber())
                                .cidName("FCC")
                                .build()
                        )
                    )
                    .build()
            )
            .build();
        flowActions.executeFNode(
            call,
            agentSide ? FlowActionType.DIAL_AGENT : FlowActionType.DIAL_CUSTOMER,
            command,
            "dial-" + channelUuid
        );
    }

}
