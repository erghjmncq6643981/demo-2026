package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeBridgeDTO;
import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.nats.FccProperties;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.outbound.application.DialAttemptGuard;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 执行坐席先接听外呼和通知外呼两个固定流程。
 *
 * <p>本服务先持久化业务意图和坐席占用，再通过 Sidecar 发起呼叫；通道事件按稳定的
 * Call、Leg 和控制标识推进流程，不把 FreeSWITCH Channel UUID 当作业务通话标识。</p>
 */
@Service
@RequiredArgsConstructor
public class OutboundCallService implements SystemFlowRuntime {

    private static final String TEMPLATE_AGENT_FIRST = "AGENT_FIRST";
    private static final String TEMPLATE_NOTIFICATION = "NOTIFICATION";
    private static final String DATA_TERMINAL = "terminal";

    private static final Set<FlowActionType> AGENT_FIRST_ACTIONS = Set.of(
        FlowActionType.VALIDATE_ROUTE_AND_RESERVE_AGENT,
        FlowActionType.DIAL_AGENT,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.CHANNEL_BRIDGE,
        FlowActionType.WAIT_FOR_HANGUP,
        FlowActionType.FINALIZE_OUTBOUND
    );
    private static final Set<FlowActionType> NOTIFICATION_ACTIONS = Set.of(
        FlowActionType.VALIDATE_NOTIFICATION_AND_ROUTE,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.READ_DTMF,
        FlowActionType.PERSIST_CONFIRMATION_AND_HANGUP,
        FlowActionType.FINALIZE_NOTIFICATION
    );

    private final AgentIdentityService identity;
    private final AgentRuntimeMapper agents;
    private final OutboundRoutePolicy routes;
    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;
    private final FccProperties properties;
    private final ScreenPopService screenPop;
    private final AgentWebSocketService websocket;
    private final TransactionTemplate transactions;
    private final DialAttemptGuard attemptGuard;

    @Value("${fcc.outbound.notification-file:}")
    private String notificationFile;

    /**
     * 返回本服务负责的两个外呼模板。
     *
     * @return 外呼模板集合
     */
    @Override
    public Set<String> templates() {
        return Set.of(TEMPLATE_AGENT_FIRST, TEMPLATE_NOTIFICATION);
    }

    /**
     * 返回指定外呼模板由本服务实际执行的公共动作。
     *
     * @param template 固定模板代码
     * @return 对应动作集合；不支持的模板返回空集合
     */
    @Override
    public Set<FlowActionType> supportedActions(String template) {
        return switch (template) {
            case TEMPLATE_AGENT_FIRST -> AGENT_FIRST_ACTIONS;
            case TEMPLATE_NOTIFICATION -> NOTIFICATION_ACTIONS;
            default -> Set.of();
        };
    }

    /**
     * 为自动外呼尝试创建通知型通话并持久化意图。
     *
     * @param owner 任务所属坐席工号
     * @param number 被叫号码
     * @param attemptId 自动外呼尝试标识
     * @return 通话标识和受理状态
     * @throws ResponseStatusException 通知媒体或出局路由不可用
     */
    public Map<String, Object> startNotificationFor(String owner, String number, String attemptId) {
        if (notificationFile.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未配置通知提示音");
        }
        var route = routes.resolve(number);
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_NOTIFICATION);
        data.put("dialJobId", attemptId);
        data.put("guestDialString", route.dialString());
        data.put("primaryWorkNo", owner);

        CallInfoBO call = CallInfoBO.builder()
            .callId(IdUtil.getCallId())
            .ctrlId(IdUtil.getCtrlId("notification"))
            .nodeId(properties.getDefaultNodeId())
            .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name())
            .direction(DirectionType.OUTBOUND)
            .stageState(CallStageState.CALLING)
            .callerNumber(route.caller())
            .destinationNumber(route.number())
            .agentWorkNo(owner)
            .guestChannelUuid(IdUtil.getUuid())
            .data(data)
            .build();
        call.putData("guestChannelUuid", call.getGuestChannelUuid());
        call.putData("nodeId", call.getNodeId());

        flowActions.executeInternal(
            call,
            FlowActionType.VALIDATE_NOTIFICATION_AND_ROUTE,
            () -> {
                transactions.executeWithoutResult(status -> {
                    attemptGuard.beforePersist(attemptId);
                    persistence.saveOrUpdateSession(call);
                });
                sessions.registerSession(call);
                return true;
            }
        );
        dial(call, false);
        return Map.of("callId", call.getCallId(), "status", "ACCEPTED");
    }

    /**
     * 保存通知外呼中的客户确认按键。
     *
     * @param call 当前通话
     * @param digit 客户输入的按键
     * @return 当前事件是否属于通知外呼模板
     */
    public boolean digits(CallInfoBO call, String digit) {
        if (!TEMPLATE_NOTIFICATION.equals(call.getDataStr("runtimeTemplate", ""))) {
            return false;
        }
        if ("1".equals(digit)) {
            flowActions.executeInternal(
                call,
                FlowActionType.PERSIST_CONFIRMATION_AND_HANGUP,
                () -> {
                    call.putData("notificationConfirmed", true);
                    persistence.saveOrUpdateSession(call);
                    client.hangup(
                        call.getNodeId(),
                        call.getCtrlId(),
                        call.getGuestChannelUuid(),
                        "NORMAL_CLEARING"
                    );
                    return true;
                }
            );
        }
        return true;
    }

    /**
     * 以当前登录坐席身份发起人工外呼。
     *
     * @param requestedOwner 请求中携带的坐席工号，可为空
     * @param number 被叫号码
     * @return 通话标识和受理状态
     * @throws ResponseStatusException 请求身份与登录坐席不一致
     */
    public Map<String, Object> start(String requestedOwner, String number) {
        var actor = identity.requirePrincipal();
        if (requestedOwner != null && !actor.workNo().equals(requestedOwner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "坐席身份不匹配");
        }
        return startFor(actor.workNo(), number, null);
    }

    /**
     * 以持久任务或回拨任务身份启动坐席先接听外呼。
     *
     * @param owner 目标坐席工号
     * @param number 被叫号码
     * @param taskId 调度任务标识，可为空
     * @return 通话、控制标识和受理状态
     * @throws ResponseStatusException 坐席话机未绑定或坐席不能被原子预占
     */
    public Map<String, Object> startFor(String owner, String number, String taskId) {
        Map<String, Object> agent = agents.agent(owner);
        if (
            agent == null ||
            agent.get("extension") == null ||
            !agent.get("extension").toString().matches("[0-9]{2,20}")
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "坐席没有可用话机绑定");
        }

        var route = routes.resolve(number);
        String callId = IdUtil.getCallId();
        String extension = agent.get("extension").toString();
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_AGENT_FIRST);
        data.put("primaryWorkNo", owner);
        data.put("guestDialString", route.dialString());
        data.put("agentExt", extension);
        if (taskId != null) {
            data.put("dialJobId", taskId);
        }

        CallInfoBO call = CallInfoBO.builder()
            .callId(callId)
            .ctrlId(IdUtil.getCtrlId("outbound"))
            .nodeId(properties.getDefaultNodeId())
            .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
            .direction(DirectionType.OUTBOUND)
            .stageState(CallStageState.CALLING)
            .callerNumber(route.caller())
            .destinationNumber(route.number())
            .agentWorkNo(owner)
            .agentExt(extension)
            .agentChannelUuid(IdUtil.getUuid())
            .guestChannelUuid(IdUtil.getUuid())
            .data(data)
            .build();
        call.putData("agentChannelUuid", call.getAgentChannelUuid());
        call.putData("guestChannelUuid", call.getGuestChannelUuid());
        call.putData("nodeId", call.getNodeId());

        flowActions.executeInternal(
            call,
            FlowActionType.VALIDATE_ROUTE_AND_RESERVE_AGENT,
            () -> {
                transactions.executeWithoutResult(status -> {
                    attemptGuard.beforePersist(taskId);
                    agents.ensurePresence(owner);
                    if (agents.reserve(owner, callId) != 1) {
                        throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "坐席未就绪或已被其他通话占用"
                        );
                    }
                    persistence.saveOrUpdateSession(call);
                });
                sessions.registerSession(call);
                return true;
            }
        );
        dial(call, true);
        screenPop.pushForAgentLeg(call, owner, extension, 30);
        return Map.of(
            "callId",
            callId,
            "ctrlId",
            call.getCtrlId(),
            "status",
            "ACCEPTED"
        );
    }

    /**
     * 按外呼模板处理一个标准化通道事件。
     *
     * @param call 当前业务通话
     * @param params Sidecar 标准化通道事件
     * @return 当前事件是否属于本服务负责的模板
     */
    public boolean event(CallInfoBO call, JsonNode params) {
        String template = call.getDataStr("runtimeTemplate", "");
        if (!templates().contains(template)) {
            return false;
        }

        synchronized (call) {
            ChannelEventState eventState = ChannelEventState.fromWireValue(
                params.path(FccEventField.STATE.getWireName()).asText()
            );
            String state = eventState.getWireValue();
            String channelUuid = params.path(FccEventField.CHANNEL_UUID.getWireName()).asText();
            if (call.getData().containsKey(DATA_TERMINAL)) {
                return true;
            }
            if (
                !channelUuid.equals(call.getAgentChannelUuid()) &&
                !channelUuid.equals(call.getGuestChannelUuid())
            ) {
                return true;
            }

            persistLeg(
                call,
                channelUuid,
                state,
                params.path(FccEventField.CAUSE.getWireName()).asText(null)
            );
            if (TEMPLATE_NOTIFICATION.equals(template) && eventState == ChannelEventState.READY) {
                startNotificationPrompt(call);
            } else if (
                eventState == ChannelEventState.READY &&
                channelUuid.equals(call.getAgentChannelUuid())
            ) {
                startCustomerLeg(call);
            } else if (
                eventState == ChannelEventState.READY &&
                channelUuid.equals(call.getGuestChannelUuid())
            ) {
                bridgeAgentAndCustomer(call);
            } else if (eventState == ChannelEventState.BRIDGE) {
                markConnected(call);
            } else if (eventState == ChannelEventState.DESTROY) {
                finish(call, template, channelUuid, params);
            }
        }
        return true;
    }

    /**
     * 保存当前外呼话道事实。
     *
     * @param call 当前业务通话
     * @param channelUuid 话道标识
     * @param state 标准话道状态
     * @param cause 挂机原因，可为空
     */
    private void persistLeg(CallInfoBO call, String channelUuid, String state, String cause) {
        persistence.saveOrUpdateLeg(
            CallLegEntity.builder()
                .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                .channelUuid(channelUuid)
                .nodeId(call.getNodeId())
                .roleType(
                    channelUuid.equals(call.getAgentChannelUuid()) ? "AGENT" : "CUSTOMER"
                )
                .direction("OUTBOUND")
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
     * 向通知外呼客户播放媒体并收取确认按键。
     *
     * @param call 当前通知通话
     */
    private void startNotificationPrompt(CallInfoBO call) {
        if (call.getData().putIfAbsent("notificationStarted", true) != null) {
            return;
        }
        call.setStageState(CallStageState.CONNECTED);
        persistence.saveOrUpdateSession(call);
        flowActions.executeFNode(
            call,
            FlowActionType.READ_DTMF,
            FNodeReadDTMFDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .media(MediaInfo.builder().type("FILE").data(notificationFile).build())
                    .minDigits(1)
                    .maxDigits(1)
                    .tries(1)
                    .timeout(10)
                    .digitTimeout(2_000)
                    .terminators("#")
                    .regex("^[1]$")
                    .actionAfter("HANGUP")
                    .build(),
            "notification-dtmf-" + call.getCallId()
        );
    }

    /**
     * 坐席话道就绪后持久化进度并呼叫客户。
     *
     * @param call 当前坐席先接听通话
     */
    private void startCustomerLeg(CallInfoBO call) {
        if (call.getData().putIfAbsent("agentReady", true) != null) {
            return;
        }
        persistence.saveOrUpdateSession(call);
        dial(call, false);
    }

    /**
     * 双方话道就绪后下发桥接指令。
     *
     * @param call 当前坐席先接听通话
     */
    private void bridgeAgentAndCustomer(CallInfoBO call) {
        if (
            !call.getData().containsKey("agentReady") ||
            call.getData().putIfAbsent("bridgeRequested", true) != null
        ) {
            return;
        }
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
    private void markConnected(CallInfoBO call) {
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
     * 幂等结算外呼并释放坐席占用。
     *
     * @param call 当前通话
     * @param template 固定模板代码
     * @param destroyedChannelUuid 已结束话道标识
     * @param params 标准化通道事件
     */
    private void finish(
        CallInfoBO call,
        String template,
        String destroyedChannelUuid,
        JsonNode params
    ) {
        FlowActionType action = TEMPLATE_AGENT_FIRST.equals(template)
            ? FlowActionType.FINALIZE_OUTBOUND
            : FlowActionType.FINALIZE_NOTIFICATION;
        flowActions.executeInternal(
            call,
            action,
            () -> {
                finishInternal(call, template, destroyedChannelUuid, params);
                return call.getHangupCause();
            }
        );
    }

    /**
     * 保存外呼终态、释放坐席并挂断存量对端话道。
     *
     * @param call 当前通话
     * @param template 固定模板代码
     * @param destroyedChannelUuid 已结束话道标识
     * @param params 标准化通道事件
     */
    private void finishInternal(
        CallInfoBO call,
        String template,
        String destroyedChannelUuid,
        JsonNode params
    ) {
        CallStageState previousStage = call.getStageState();
        call.putData(DATA_TERMINAL, true);
        call.setHangupCause(
            params.path(FccEventField.CAUSE.getWireName()).asText("NORMAL_CLEARING")
        );
        call.setStageState(CallStageState.NORMAL_END);
        call.setDuration(params.path(FccEventField.DURATION.getWireName()).asInt());
        call.setBillsec(params.path(FccEventField.BILL_SECONDS.getWireName()).asInt());

        try {
            transactions.executeWithoutResult(status -> {
                persistence.saveOrUpdateSession(call);
                if (TEMPLATE_AGENT_FIRST.equals(template)) {
                    agents.release(call.getAgentWorkNo(), call.getCallId());
                }
            });
        } catch (RuntimeException failure) {
            call.getData().remove(DATA_TERMINAL);
            call.setStageState(previousStage);
            throw failure;
        }

        String peerUuid = destroyedChannelUuid.equals(call.getAgentChannelUuid())
            ? call.getGuestChannelUuid()
            : call.getAgentChannelUuid();
        if (peerUuid != null) {
            client.hangup(call.getNodeId(), call.getCtrlId(), peerUuid, "NORMAL_CLEARING");
        }
        if (TEMPLATE_AGENT_FIRST.equals(template)) {
            websocket.pushCallHangup(
                call.getAgentWorkNo(),
                call.getCallId(),
                Map.of("cause", call.getHangupCause())
            );
        }
        sessions.removeSession(call.getCtrlId());
    }

    /**
     * 使用预分配话道 UUID 发起坐席或客户侧拨号。
     *
     * @param call 当前通话
     * @param agentSide 是否呼叫坐席侧
     */
    private void dial(CallInfoBO call, boolean agentSide) {
        String channelUuid = agentSide
            ? call.getAgentChannelUuid()
            : call.getGuestChannelUuid();
        String destination = agentSide
            ? "user/" + call.getAgentExt()
            : call.getDataStr("guestDialString", "");
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
