package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeBridgeDTO;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 固定 DID/技能组呼入流程，排队状态与已尝试坐席保存到通话事实。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundCallService implements SystemFlowRuntime {

    private static final String TEMPLATE = "INBOUND";
    private static final Set<FlowActionType> SUPPORTED_ACTIONS = Set.of(
        FlowActionType.RESOLVE_DID_AND_PARK,
        FlowActionType.READ_DTMF,
        FlowActionType.SELECT_DIGIT_ROUTE,
        FlowActionType.RESERVE_AND_DIAL_AGENT,
        FlowActionType.CHANNEL_BRIDGE,
        FlowActionType.WAIT_FOR_HANGUP,
        FlowActionType.FINALIZE_INBOUND
    );

    private final AgentRuntimeMapper agents;
    private final CallPersistenceService persistence;
    private final CallSessionManager sessions;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;
    private final ScreenPopService screenPop;
    private final AgentWebSocketService websocket;
    private final TransactionTemplate transactions;
    private final InboundMenuService menu;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 返回呼入固定模板代码。
     *
     * @return 呼入模板集合
     */
    @Override
    public Set<String> templates() {
        return Set.of(TEMPLATE);
    }

    /**
     * 返回呼入服务实际执行的公共动作。
     *
     * @param template 固定模板代码
     * @return 呼入动作集合；其他模板返回空集合
     */
    @Override
    public Set<FlowActionType> supportedActions(String template) {
        return TEMPLATE.equals(template) ? SUPPORTED_ACTIONS : Set.of();
    }

    /**
     * 接管普通呼入，只使用 DID 唯一绑定的固定发布版本或 group:组代码。
     *
     * @param call 通话
     * @param params 事件
     * @return 是否消费
     */
    public boolean event(CallInfoBO call, JsonNode params) {
        if (
            call.getDirection() != DirectionType.INBOUND ||
            "0000".equals(call.getDestinationNumber())
        ) return false;
        synchronized (call) {
            ChannelEventState eventState = ChannelEventState.fromWireValue(
                params.path(FccEventField.STATE.getWireName()).asText()
            );
            String state = eventState.getWireValue();
            String uuid = params.path(FccEventField.CHANNEL_UUID.getWireName()).asText();
            if (eventState == ChannelEventState.START && !call.getData().containsKey("runtimeTemplate")) {
                flowActions.executeInternal(
                    call,
                    FlowActionType.RESOLVE_DID_AND_PARK,
                    () -> initialize(call, uuid)
                );
            }
            if (!"INBOUND".equals(call.getDataStr("runtimeTemplate", ""))) return true;
            if (call.getData().containsKey("terminal")) return true;
            // Late events from a previously rejected agent must not answer/end the new attempt.
            if (
                !uuid.equals(call.getGuestChannelUuid()) && !uuid.equals(call.getAgentChannelUuid())
            ) return true;
            persistence.saveOrUpdateLeg(
                CallLegEntity.builder()
                    .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                    .channelUuid(uuid)
                    .nodeId(call.getNodeId())
                    .roleType(uuid.equals(call.getGuestChannelUuid()) ? "CUSTOMER" : "AGENT")
                    .direction(uuid.equals(call.getGuestChannelUuid()) ? "INBOUND" : "OUTBOUND")
                    .state(state)
                    .hangupCause(params.path(FccEventField.CAUSE.getWireName()).asText(null))
                    .endedAt(
                        eventState == ChannelEventState.DESTROY
                            ? LocalDateTime.now(ZoneOffset.UTC)
                            : null
                    )
                    .build()
            );
            if (eventState == ChannelEventState.READY && uuid.equals(call.getGuestChannelUuid())) {
                call.putData("guestReady", true);
                if (menu.ready(call)) return true;
                call.putData("flowBranch", "menu.enabled=false");
                persistence.saveOrUpdateSession(call);
                route(call);
            } else if (eventState == ChannelEventState.READY && uuid.equals(call.getAgentChannelUuid())) {
                if (call.getData().putIfAbsent("bridgeRequested", true) == null) {
                    persistence.saveOrUpdateSession(call);
                    flowActions.executeFNode(
                        call,
                        FlowActionType.CHANNEL_BRIDGE,
                        FNodeBridgeDTO.builder()
                            .ctrlUuid(call.getCtrlId())
                            .uuid(call.getGuestChannelUuid())
                            .peerUuid(uuid)
                            .build(),
                        "bridge-" + call.getCallId()
                    );
                }
            } else if (eventState == ChannelEventState.BRIDGE) {
                flowActions.executeInternal(
                    call,
                    FlowActionType.WAIT_FOR_HANGUP,
                    () -> {
                        call.setStageState(CallStageState.CONNECTED);
                        persistence.saveOrUpdateSession(call);
                        websocket.pushCallAnswered(
                            call.getAgentWorkNo(),
                            call.getCallId(),
                            Map.of("callId", call.getCallId())
                        );
                        return true;
                    }
                );
            } else if (eventState == ChannelEventState.DESTROY) {
                if (
                    uuid.equals(call.getGuestChannelUuid()) ||
                    call.getStageState() == CallStageState.CONNECTED
                ) {
                    finish(
                        call,
                        params.path(FccEventField.CAUSE.getWireName()).asText("NORMAL_CLEARING")
                    );
                } else if (uuid.equals(call.getAgentChannelUuid())) {
                    websocket.pushCallHangup(
                        call.getAgentWorkNo(),
                        call.getCallId(),
                        Map.of(
                            "cause",
                            params.path(FccEventField.CAUSE.getWireName()).asText("NO_ANSWER")
                        )
                    );
                    call.getData().remove("bridgeRequested");
                    release(call);
                    call.setAgentChannelUuid(null);
                    call.setAgentWorkNo(null);
                    call.getData().remove("agentChannelUuid");
                    call.getData().remove("primaryWorkNo");
                    call.getData().remove("screen_pop_pushed");
                    persistence.saveOrUpdateSession(call);
                    route(call);
                }
            }
        }
        return true;
    }

    /**
     * 定时处理持久队列恢复后的待分配通话，客户已结束则不会再拨坐席。
     */
    @Scheduled(fixedDelay = 2000)
    public void routeWaiting() {
        for (var call : sessions.snapshot())
            if ("INBOUND".equals(call.getDataStr("runtimeTemplate", ""))) try {
                synchronized (call) {
                    route(call);
                }
            } catch (RuntimeException e) {
                log.warn("[呼入排队] 等待恢复 callId={}", call.getCallId());
            }
    }

    /**
     * 解析 DID 绑定，并将固定版本或技能组路由写入通话上下文。
     *
     * @param call 当前呼入通话
     * @param channelUuid 客户话道标识
     * @return 是否成功初始化呼入模型
     */
    private Boolean initialize(CallInfoBO call, String channelUuid) {
        var routes = agents.inbound(call.getDestinationNumber());
        if (routes.size() != 1) {
            client.hangup(
                call.getNodeId(),
                call.getCtrlId(),
                channelUuid,
                "UNALLOCATED_NUMBER"
            );
            sessions.removeSession(call.getCtrlId());
            return false;
        }
        var route = routes.getFirst();
        String key = String.valueOf(route.get("routeKey"));
        call.putData("runtimeTemplate", "INBOUND");
        call.putData("nodeId", call.getNodeId());
        call.putData("guestChannelUuid", call.getGuestChannelUuid());
        call.putData("queueDeadline", System.currentTimeMillis() + 120000);
        call.putData("triedAgents", new ArrayList<String>());
        if (key.startsWith("group:")) {
            call.putData("groupCode", key.substring(6));
        } else {
            try {
                menu.configure(call, String.valueOf(route.get("definition")));
                call.putData("flowVersionId", String.valueOf(route.get("versionId")));
            } catch (Exception invalid) {
                call.getData().remove("runtimeTemplate");
                client.hangup(call.getNodeId(), call.getCtrlId(), channelUuid, "CALL_REJECTED");
                sessions.removeSession(call.getCtrlId());
                return false;
            }
        }
        call.setStageState(CallStageState.CALLING);
        persistence.saveOrUpdateSession(call);
        return true;
    }

    /**
     * 在同一事务内预占并保存选中目标。
     *
     * @param call 排队通话
     */
    @SuppressWarnings("unchecked")
    private void route(CallInfoBO call) {
        flowActions.executeInternal(
            call,
            FlowActionType.RESERVE_AND_DIAL_AGENT,
            () -> {
                routeInternal(call);
                return call.getAgentWorkNo();
            }
        );
    }

    /**
     * 执行一次幂等呼入路由尝试。
     *
     * @param call 排队通话
     */
    @SuppressWarnings("unchecked")
    private void routeInternal(CallInfoBO call) {
        if (
            call.getData().containsKey("terminal") ||
            !call.getData().containsKey("guestReady") ||
            call.getAgentChannelUuid() != null
        ) return;
        if (call.getData().containsKey("ivrWaiting")) {
            if (System.currentTimeMillis() > ((Number) call.getData().get("ivrDeadline")).longValue()) {
                call.putData("flowBranch", "menu.timeout");
                finish(call, "NO_USER_RESPONSE");
            }
            return;
        }
        if (System.currentTimeMillis() > ((Number) call.getData().get("queueDeadline")).longValue()) {
            finish(call, "NO_ANSWER");
            return;
        }
        List<String> tried = (List<String>) call.getData().get("triedAgents");
        String direct = call.getDataStr("directOwner", null);
        Map<String, Object> candidate = direct != null
            ? agents.agent(direct)
            : agents.candidate(call.getDataStr("groupCode", ""), tried);
        if (candidate == null || candidate.get("extension") == null) return;
        String owner = candidate.get("workNo").toString(),
            extension = candidate.get("extension").toString();
        if (tried.contains(owner) || !extension.matches("[0-9]{2,20}")) return;
        var previousData = new HashMap<String, Object>(call.getData());
        previousData.put("triedAgents", new ArrayList<>(tried));
        String previousOwner = call.getAgentWorkNo(),
            previousExtension = call.getAgentExt(),
            previousChannel = call.getAgentChannelUuid();
        Boolean reserved;
        try {
            reserved = transactions.execute(transaction -> {
                if (agents.reserve(owner, call.getCallId()) != 1) return false;
                call.setAgentWorkNo(owner);
                call.setAgentExt(extension);
                call.setAgentChannelUuid(IdUtil.getUuid());
                tried.add(owner);
                call.putData("primaryWorkNo", owner);
                call.putData("agentExt", extension);
                call.putData("agentChannelUuid", call.getAgentChannelUuid());
                persistence.saveOrUpdateSession(call);
                return true;
            });
        } catch (RuntimeException failure) {
            call.setAgentWorkNo(previousOwner);
            call.setAgentExt(previousExtension);
            call.setAgentChannelUuid(previousChannel);
            call.setData(previousData);
            throw failure;
        }
        if (!Boolean.TRUE.equals(reserved)) return;
        sessions.bindChannel(call.getAgentChannelUuid(), call.getCtrlId());
        var dto = FNodeDialDTO.builder()
            .ctrlUuid(call.getCtrlId())
            .uuid(call.getAgentChannelUuid())
            .timeout(30)
            .destination(
                FNodeDialDTO.Destination.builder()
                    .callParams(
                        List.of(
                            FNodeDialDTO.CallParam.builder()
                                .uuid(call.getAgentChannelUuid())
                                .dialString("user/" + extension)
                                .cidNumber(call.getCallerNumber())
                                .cidName("FCC")
                                .build()
                        )
                    )
                    .build()
            )
            .build();
        CallControlService.requireAccepted(client.dial(call.getNodeId(), dto));
        screenPop.pushForAgentLeg(call, owner, extension, 30);
    }

    /**
     * 幂等结束，未接听追加漏话回拨。
     *
     * @param call 通话
     * @param cause 原因
     */
    private void finish(CallInfoBO call, String cause) {
        flowActions.executeInternal(
            call,
            FlowActionType.FINALIZE_INBOUND,
            () -> {
                finishInternal(call, cause);
                return call.getHangupCause();
            }
        );
    }

    /**
     * 保存呼入终态、释放坐席并按配置生成回拨待办。
     *
     * @param call 当前通话
     * @param cause 终止原因
     */
    private void finishInternal(CallInfoBO call, String cause) {
        if (call.getData().putIfAbsent("terminal", true) != null) return;
        boolean answered = call.getStageState() == CallStageState.CONNECTED;
        var previousStage = call.getStageState();
        call.setStageState(CallStageState.NORMAL_END);
        call.setHangupCause(cause);
        try {
            transactions.executeWithoutResult(transaction -> {
                persistence.saveOrUpdateSession(call);
                release(call);
                if (
                    !answered && !"HANGUP".equals(call.getDataStr("ivrTimeoutAction", "CALLBACK"))
                ) agents.callback(
                    IdUtil.nextId(),
                    call.getCallId(),
                    call.getCallerNumber(),
                    call.getDestinationNumber(),
                    cause
                );
            });
        } catch (RuntimeException failure) {
            call.getData().remove("terminal");
            call.setStageState(previousStage);
            throw failure;
        }
        client.hangup(call.getNodeId(), call.getCtrlId(), call.getGuestChannelUuid(), "NORMAL_CLEARING");
        if (call.getAgentChannelUuid() != null) {
            client.hangup(call.getNodeId(), call.getCtrlId(), call.getAgentChannelUuid(), "NORMAL_CLEARING");
            websocket.pushCallHangup(call.getAgentWorkNo(), call.getCallId(), Map.of("cause", cause));
        }
        sessions.removeSession(call.getCtrlId());
    }

    /**
     * 仅释放本次占用。
     *
     * @param call 通话
     */
    private void release(CallInfoBO call) {
        if (call.getAgentWorkNo() != null) {
            agents.release(call.getAgentWorkNo(), call.getCallId());
        }
    }

    /**
     * 只处理本呼入固定模板的客户按键，不回落旧路由逻辑。
     *
     * @param call 通话
     * @param params 按键事件
     * @return 是否为本模板
     */
    public boolean digits(CallInfoBO call, JsonNode params) {
        if (!"INBOUND".equals(call.getDataStr("runtimeTemplate", ""))) return false;
        synchronized (call) {
            if (!call.getData().containsKey("terminal") && menu.digits(call, params)) route(call);
        }
        return true;
    }
}
