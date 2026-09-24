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
import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccCommandResultStatus;
import com.chandler.fcc.common.protocol.FccEventParameter;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.domain.AgentWorkStatus;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.outbound.application.DialAttemptGuard;
import com.chandler.fcc.server.recording.application.CallRecordingService;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 执行系统先呼坐席、坐席终端主动呼出和通知外呼固定流程。
 *
 * <p>本服务先持久化业务意图和坐席占用，再通过 Sidecar 发起呼叫；通道事件按稳定的
 * Call、Leg 和控制标识推进流程，不把 FreeSWITCH Channel UUID 当作业务通话标识。</p>
 */
@Service
@RequiredArgsConstructor
public class OutboundCallService implements SystemFlowRuntime {

    private static final String TEMPLATE_AGENT_FIRST = "AGENT_FIRST";
    private static final String TEMPLATE_AGENT_ORIGINATED = "AGENT_ORIGINATED";
    private static final String TEMPLATE_NOTIFICATION = "NOTIFICATION";
    private static final String FLOW_SYSTEM_NOTIFICATION = "SYSTEM_NOTIFICATION";
    private static final String DATA_TERMINAL = "terminal";

    private static final Set<FlowActionType> AGENT_FIRST_ACTIONS = Set.of(
        FlowActionType.VALIDATE_ROUTE_AND_RESERVE_AGENT,
        FlowActionType.DIAL_AGENT,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.CHANNEL_BRIDGE,
        FlowActionType.START_RECORDING,
        FlowActionType.WAIT_FOR_HANGUP,
        FlowActionType.STOP_RECORDING,
        FlowActionType.FINALIZE_OUTBOUND
    );
    private static final Set<FlowActionType> NOTIFICATION_ACTIONS = Set.of(
        FlowActionType.VALIDATE_NOTIFICATION_AND_ROUTE,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.READ_DTMF,
        FlowActionType.PERSIST_CONFIRMATION_AND_HANGUP,
        FlowActionType.FINALIZE_NOTIFICATION
    );
    private static final Set<FlowActionType> AGENT_ORIGINATED_ACTIONS = Set.of(
        FlowActionType.ACCEPT_AGENT_ORIGINATED_CALL,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.CHANNEL_BRIDGE,
        FlowActionType.START_RECORDING,
        FlowActionType.WAIT_FOR_HANGUP,
        FlowActionType.STOP_RECORDING,
        FlowActionType.FINALIZE_OUTBOUND
    );

    private final AgentIdentityService identity;
    private final AgentRuntimeMapper agents;
    private final OutboundRoutePolicy routes;
    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;
    private final FccClient client;
    private final FlowActionExecutionService flowActions;
    private final FlowConfig flowConfig;
    private final ScreenPopService screenPop;
    private final AgentWebSocketService websocket;
    private final TransactionTemplate transactions;
    private final DialAttemptGuard attemptGuard;
    private final CallRecordingService recordings;

    /**
     * 返回本服务负责的外呼模板。
     *
     * @return 外呼模板集合
     */
    @Override
    public Set<String> templates() {
        return Set.of(TEMPLATE_AGENT_FIRST, TEMPLATE_AGENT_ORIGINATED, TEMPLATE_NOTIFICATION);
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
            case TEMPLATE_AGENT_ORIGINATED -> AGENT_ORIGINATED_ACTIONS;
            case TEMPLATE_NOTIFICATION -> NOTIFICATION_ACTIONS;
            default -> Set.of();
        };
    }

    /**
     * 为流程型自动外呼创建客户侧通话并持久化意图。
     *
     * @param number 被叫号码
     * @param attemptId 自动外呼尝试标识
     * @param text 本次任务通知文案
     * @param confirmDigit 客户确认按键
     * @param timeoutSeconds 等待客户确认的秒数
     * @return 通话标识和受理状态
     * @throws ResponseStatusException 通知媒体或出局路由不可用
     */
    public Map<String, Object> startAutoDial(
        String number,
        String attemptId,
        String text,
        String confirmDigit,
        int timeoutSeconds
    ) {
        FlowConfig.FlowSnapshot flow = requireNotificationFlow();
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank() || normalizedText.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通知文案不能为空且不能超过1000个字符");
        }
        if (confirmDigit == null || !confirmDigit.matches("[0-9]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认按键只支持一位数字");
        }
        if (timeoutSeconds < 3 || timeoutSeconds > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认等待时间必须为3至60秒");
        }
        var route = routes.resolve(number);
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_NOTIFICATION);
        data.put("dialJobId", attemptId);
        data.put("flowKey", FLOW_SYSTEM_NOTIFICATION);
        data.put("notificationText", normalizedText);
        data.put("confirmDigit", confirmDigit);
        data.put("notificationTimeoutSeconds", timeoutSeconds);
        data.put("guestNumber", route.number());
        data.put("guestContext", route.context());

        CallInfoBO call = CallInfoBO.builder()
            .callId(IdUtil.getCallId())
            .ctrlId(IdUtil.getCtrlId("notification"))
            .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name())
            .direction(DirectionType.OUTBOUND)
            .stageState(CallStageState.CALLING)
            .callerNumber(route.caller())
            .destinationNumber(route.number())
            .guestChannelUuid(IdUtil.getUuid())
            .data(data)
            .build();
        pinFlow(call, flow);
        call.putData("guestChannelUuid", call.getGuestChannelUuid());

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
     * 使用通知外呼 ReadDTMF 的最终指令结果保存客户确认。
     *
     * @param call 当前通话
     * @param params Sidecar 规范指令结果参数
     * @return 当前结果是否属于通知外呼固定模型
     */
    public boolean commandResult(CallInfoBO call, JsonNode params) {
        if (!TEMPLATE_NOTIFICATION.equals(call.getDataStr("runtimeTemplate", ""))) {
            return false;
        }
        if (!("notification-dtmf-" + call.getCallId()).equals(
            params.path(FccEventField.COMMAND_ID.getWireName()).asText()
        )) {
            return true;
        }
        synchronized (call) {
            if (
                call.getData().containsKey(DATA_TERMINAL) ||
                call.getData().containsKey("notificationHangupRequested")
            ) {
                return true;
            }
            boolean succeeded = FccCommandResultStatus.fromWireValue(
                params.path(FccEventField.COMMAND_STATUS.getWireName()).asText()
            ) == FccCommandResultStatus.SUCCEEDED;
            String digit = params.path(FccEventField.RESULT.getWireName()).path("dtmf").asText();
            if (succeeded && call.getDataStr("confirmDigit", "1").equals(digit)) {
                flowActions.executeInternal(
                    call,
                    FlowActionType.PERSIST_CONFIRMATION_AND_HANGUP,
                    () -> {
                        call.putData("notificationConfirmed", true);
                        persistence.saveOrUpdateSession(call);
                        return true;
                    }
                );
            }
            if (call.getData().putIfAbsent("notificationHangupRequested", true) == null) {
                persistence.saveOrUpdateSession(call);
                client.hangup(
                    call.getCtrlId(),
                    call.getGuestChannelUuid(),
                    succeeded ? "NORMAL_CLEARING" : "NORMAL_TEMPORARY_FAILURE"
                );
            }
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "坐席当前接听终端未绑定或未启用");
        }

        var route = routes.resolve(number);
        String callId = IdUtil.getCallId();
        String extension = agent.get("extension").toString();
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_AGENT_FIRST);
        data.put("primaryWorkNo", owner);
        data.put("guestNumber", route.number());
        data.put("guestContext", route.context());
        data.put("agentExt", extension);
        data.put("agentEndpointType", String.valueOf(agent.get("endpointType")));
        if (taskId != null) {
            data.put("dialJobId", taskId);
        }

        CallInfoBO call = CallInfoBO.builder()
            .callId(callId)
            .ctrlId(IdUtil.getCtrlId("outbound"))
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
        pinFlow(call, TEMPLATE_AGENT_FIRST);
        call.putData("agentChannelUuid", call.getAgentChannelUuid());
        call.putData("guestChannelUuid", call.getGuestChannelUuid());

        flowActions.executeInternal(
            call,
            FlowActionType.VALIDATE_ROUTE_AND_RESERVE_AGENT,
            () -> {
                transactions.executeWithoutResult(status -> {
                    attemptGuard.beforePersist(taskId);
                    agents.ensurePresence(owner);
                    if (agents.reserveOutbound(owner, callId) != 1) {
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
     * 判断首次话道事件是否来自默认拨号上下文中的已认证坐席终端。
     *
     * <p>该判断只负责区分坐席主动外呼与普通运营商呼入。匹配后即由外呼服务负责，
     * 即使分机未绑定坐席也不能回落成客户呼入。</p>
     *
     * @param params 标准化通道事件参数
     * @param state 事件话道状态
     * @return 是否为坐席终端主动外呼入口
     */
    public boolean isAgentOriginatedEntry(JsonNode params, ChannelEventState state) {
        if (
            state != ChannelEventState.START ||
            !"inbound".equalsIgnoreCase(
                params.path(FccEventField.DIRECTION.getWireName()).asText()
            ) ||
            !"default".equalsIgnoreCase(
                params.path(FccEventField.CONTEXT.getWireName()).asText()
            )
        ) {
            return false;
        }
        String number = params.path(FccEventField.DESTINATION_NUMBER.getWireName()).asText();
        String extension = params
            .path(FccEventField.PARAMETERS.getWireName())
            .path(FccEventParameter.AUTHENTICATED_EXTENSION.getWireName())
            .asText();
        return !number.isBlank() && !"0000".equals(number) && !extension.isBlank();
    }

    /**
     * 将坐席终端主动拨入形成的话道接管为外呼坐席 Leg。
     *
     * <p>该入口以 Sidecar 上报的已认证终端为身份依据，不查询注册在线投影。坐席 Leg
     * 进入 READY 后才会呼叫客户，因此与“系统先呼叫坐席”的模板保持两个独立入口。</p>
     *
     * @param params 标准化通道事件参数
     * @param nodeId 事件实际来源节点
     * @param state 事件话道状态
     * @param channelUuid 坐席已建立的话道标识
     * @param controlId 事件携带的控制标识，可为空
     * @return 已接管通话；事件不属于坐席主动外呼时返回空
     * @throws ResponseStatusException 坐席被其他通话占用或出局路由不可用
     */
    public CallInfoBO createAgentOriginated(
        JsonNode params,
        String nodeId,
        ChannelEventState state,
        String channelUuid,
        String controlId
    ) {
        if (!isAgentOriginatedEntry(params, state)) {
            return null;
        }
        String number = params.path(FccEventField.DESTINATION_NUMBER.getWireName()).asText();
        String extension = params
            .path(FccEventField.PARAMETERS.getWireName())
            .path(FccEventParameter.AUTHENTICATED_EXTENSION.getWireName())
            .asText();
        Map<String, Object> agent = agents.agentByEndpoint(extension);
        if (agent == null || agent.get("workNo") == null) {
            client.hangup(controlId, channelUuid, "CALL_REJECTED");
            return null;
        }

        OutboundRoutePolicy.Route route;
        try {
            route = routes.resolve(number);
        } catch (ResponseStatusException | IllegalArgumentException rejection) {
            client.hangup(controlId, channelUuid, "UNALLOCATED_NUMBER");
            return null;
        }
        String owner = agent.get("workNo").toString();
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_AGENT_ORIGINATED);
        data.put("primaryWorkNo", owner);
        data.put("agentExt", extension);
        data.put("agentEndpointType", String.valueOf(agent.get("endpointType")));
        data.put("agentChannelUuid", channelUuid);
        data.put("guestNumber", route.number());
        data.put("guestContext", route.context());
        data.put("routingContext", params.path(FccEventField.CONTEXT.getWireName()).asText("default"));

        CallInfoBO call = CallInfoBO.builder()
            .nodeId(nodeId)
            .callId(IdUtil.getCallId())
            .ctrlId(
                controlId == null || controlId.isBlank()
                    ? IdUtil.getCtrlId("agent-outbound")
                    : controlId
            )
            .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
            .direction(DirectionType.OUTBOUND)
            .stageState(CallStageState.CALLING)
            .callerNumber(route.caller())
            .destinationNumber(route.number())
            .agentWorkNo(owner)
            .agentExt(extension)
            .agentChannelUuid(channelUuid)
            .guestChannelUuid(IdUtil.getUuid())
            .data(data)
            .build();
        pinFlow(call, TEMPLATE_AGENT_ORIGINATED);
        call.putData("guestChannelUuid", call.getGuestChannelUuid());

        try {
            flowActions.executeInternal(
                call,
                FlowActionType.ACCEPT_AGENT_ORIGINATED_CALL,
                () -> {
                    transactions.executeWithoutResult(transaction -> {
                        agents.ensurePresence(owner);
                        if (agents.reserveOriginated(owner, call.getCallId()) != 1) {
                            throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "坐席已被其他通话占用"
                            );
                        }
                        persistence.saveOrUpdateSession(call);
                    });
                    return true;
                }
            );
        } catch (ResponseStatusException rejection) {
            client.hangup(call.getCtrlId(), channelUuid, "USER_BUSY");
            return null;
        }
        sessions.registerSession(call);
        sessions.bindChannel(channelUuid, call.getCtrlId());
        sessions.bindChannel(call.getGuestChannelUuid(), call.getCtrlId());
        screenPop.pushForAgentLeg(call, owner, extension, 30);
        return call;
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
            updateAgentWorkStatus(call, eventState, channelUuid);
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
     * 在通话意图落库前固定当前发布流程版本，后续发布不会改变本通话模型。
     *
     * @param call 待持久化通话
     * @param template 固定流程模板
     * @throws ResponseStatusException 未找到可用发布版本
     */
    private void pinFlow(CallInfoBO call, String template) {
        FlowConfig.FlowSnapshot flow = flowConfig.getPublishedFlow(null, template)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "未找到已发布流程: " + template));
        pinFlow(call, flow);
    }

    /**
     * 将已加载的不可变流程版本固定到通话，避免再次查询时命中其他版本。
     *
     * @param call 待持久化通话
     * @param flow 已校验发布快照
     */
    private void pinFlow(CallInfoBO call, FlowConfig.FlowSnapshot flow) {
        call.putData("flowDefinitionId", flow.definitionId());
        call.putData("flowVersionId", flow.versionId());
    }

    /**
     * 加载并确认系统固定通知模型可执行。
     *
     * @return 已发布通知流程快照
     */
    private FlowConfig.FlowSnapshot requireNotificationFlow() {
        FlowConfig.FlowSnapshot flow = flowConfig.getPublishedFlow(null, FLOW_SYSTEM_NOTIFICATION)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "系统通知模型尚未初始化"));
        if (!TEMPLATE_NOTIFICATION.equalsIgnoreCase(flow.modelType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统通知模型类型不正确");
        }
        return flow;
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
        boolean agentLeg = channelUuid.equals(call.getAgentChannelUuid());
        boolean agentOriginated = TEMPLATE_AGENT_ORIGINATED.equals(
            call.getDataStr("runtimeTemplate", "")
        );
        persistence.saveOrUpdateLeg(
            CallLegEntity.builder()
                .callId(CallPersistenceService.parseNumericId(call.getCallId()))
                .channelUuid(channelUuid)
                .nodeId(call.getNodeId())
                .roleType(agentLeg ? "AGENT" : "CUSTOMER")
                .direction(agentLeg && agentOriginated ? "INBOUND" : "OUTBOUND")
                .endpointType(
                    agentLeg
                        ? call.getDataStr("agentEndpointType", null)
                        : "CARRIER"
                )
                .endpointId(agentLeg ? call.getAgentExt() : null)
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
                .media(
                    MediaInfo.builder()
                        .type(FNodeMediaType.TEXT)
                        .data(call.getDataStr("notificationText", ""))
                        .build()
                )
                .minDigits(1)
                .maxDigits(1)
                .tries(1)
                .timeout(notificationTimeoutMillis(call))
                .digitTimeout(2_000)
                .terminators("#")
                .regex("^[" + call.getDataStr("confirmDigit", "1") + "]$")
                .actionAfter(FNodeDtmfPostAction.PARK)
                .build(),
            "notification-dtmf-" + call.getCallId()
        );
    }

    /**
     * 读取已固定到通话快照的确认等待时间。
     *
     * @param call 当前通知通话
     * @return 毫秒超时时间
     */
    private int notificationTimeoutMillis(CallInfoBO call) {
        Object value = call.getData().get("notificationTimeoutSeconds");
        int seconds = value instanceof Number number
            ? number.intValue()
            : Integer.parseInt(value == null ? "10" : value.toString());
        return Math.clamp(seconds, 3, 60) * 1_000;
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
        FlowActionType action = isAgentCall(template)
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
        recordings.stop(call);
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
                if (isAgentCall(template)) {
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
            client.hangup(call.getCtrlId(), peerUuid, "NORMAL_CLEARING");
        }
        if (isAgentCall(template)) {
            websocket.pushCallHangup(
                call.getAgentWorkNo(),
                call.getCallId(),
                Map.of("cause", call.getHangupCause())
            );
        }
        sessions.removeSession(call.getCtrlId());
    }

    /**
     * 使用坐席话道事件推进人工外呼工作状态，自动外呼不会进入本分支。
     *
     * @param call 当前通话
     * @param eventState 标准话道事件状态
     * @param channelUuid 事件话道标识
     */
    private void updateAgentWorkStatus(
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
    private void dial(CallInfoBO call, boolean agentSide) {
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

    /**
     * 判断模板是否包含需要占用和释放坐席的双向人工外呼。
     *
     * @param template 固定运行模板代码
     * @return 两类人工外呼返回 {@code true}
     */
    private boolean isAgentCall(String template) {
        return TEMPLATE_AGENT_FIRST.equals(template) || TEMPLATE_AGENT_ORIGINATED.equals(template);
    }
}
