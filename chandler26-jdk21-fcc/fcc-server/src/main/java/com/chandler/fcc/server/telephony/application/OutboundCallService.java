package com.chandler.fcc.server.telephony.application;

import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.TEMPLATE_AGENT_FIRST;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.TEMPLATE_AGENT_ORIGINATED;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.TEMPLATE_NOTIFICATION;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.FLOW_SYSTEM_NOTIFICATION;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.DATA_TERMINAL;
import static com.chandler.fcc.server.telephony.application.OutboundRuntimeSupport.isAgentCall;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.AutoDialTaskType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccEventParameter;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.outbound.application.DialAttemptGuard;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundCallService implements SystemFlowRuntime {

    private static final Set<FlowActionType> AGENT_FIRST_ACTIONS = Set.of(
        FlowActionType.VALIDATE_ROUTE_AND_RESERVE_AGENT,
        FlowActionType.DIAL_AGENT,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.CHANNEL_BRIDGE,
        FlowActionType.START_RECORDING,
        FlowActionType.WAIT_FOR_HANGUP,
        FlowActionType.STOP_RECORDING,
        FlowActionType.COLLECT_SERVICE_RATING,
        FlowActionType.PERSIST_SERVICE_RATING,
        FlowActionType.PLAY_CLOSING_VOICE,
        FlowActionType.FINALIZE_OUTBOUND
    );
    private static final Set<FlowActionType> NOTIFICATION_ACTIONS = Set.of(
        FlowActionType.VALIDATE_NOTIFICATION_AND_ROUTE,
        FlowActionType.DIAL_CUSTOMER,
        FlowActionType.PLAY_MEDIA,
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
        FlowActionType.COLLECT_SERVICE_RATING,
        FlowActionType.PERSIST_SERVICE_RATING,
        FlowActionType.PLAY_CLOSING_VOICE,
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
    private final TransactionTemplate transactions;
    private final DialAttemptGuard attemptGuard;
    private final InboundPostCallService postCall;
    private final OutboundMediaCoordinator media;
    private final OutboundLifecycleCoordinator lifecycle;
    private final OutboundNotificationService notification;
    private final CallAdmissionService admissionService;

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
     * @param taskType 任务类型 (NOTIFY 或 SURVEY)
     * @param confirmDigit 客户确认按键 (SURVEY 生效)
     * @param timeoutSeconds 等待客户确认的秒数 (SURVEY 生效)
     * @return 通话标识和受理状态
     * @throws ResponseStatusException 通知媒体或出局路由不可用
     */
    public Map<String, Object> startAutoDial(
        String number,
        String attemptId,
        String text,
        String taskType,
        String confirmDigit,
        Integer timeoutSeconds
    ) {
        FlowConfig.FlowSnapshot flow = requireNotificationFlow();
        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank() || normalizedText.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通知文案不能为空且不能超过1000个字符");
        }
        String normalizedTaskType = (taskType == null || taskType.isBlank())
            ? AutoDialTaskType.NOTIFY.name()
            : taskType.trim().toUpperCase();

        if (AutoDialTaskType.SURVEY.name().equals(normalizedTaskType)) {
            if (confirmDigit == null || !confirmDigit.matches("[0-9]")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认按键只支持一位数字");
            }
            if (timeoutSeconds == null || timeoutSeconds < 3 || timeoutSeconds > 60) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "确认等待时间必须为3至60秒");
            }
        }
        var riskDecision = admissionService.checkDestinationRisk(number);
        if (!riskDecision.admitted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, riskDecision.reasonMessage());
        }
        var route = routes.resolve(number);
        var data = new HashMap<String, Object>();
        data.put("runtimeTemplate", TEMPLATE_NOTIFICATION);
        data.put("dialJobId", attemptId);
        data.put("flowKey", FLOW_SYSTEM_NOTIFICATION);
        data.put("taskType", normalizedTaskType);
        data.put("notificationText", normalizedText);
        if (AutoDialTaskType.SURVEY.name().equals(normalizedTaskType)) {
            data.put("confirmDigit", confirmDigit);
            data.put("notificationTimeoutSeconds", timeoutSeconds == null ? 10 : timeoutSeconds);
        }
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
        media.dial(call, false);
        return Map.of("callId", call.getCallId(), "status", "ACCEPTED");
    }

    /**
     * 兼容旧接口的自动外呼调用（默认问卷/带确认按键）。
     */
    public Map<String, Object> startAutoDial(
        String number,
        String attemptId,
        String text,
        String confirmDigit,
        int timeoutSeconds
    ) {
        return startAutoDial(
            number,
            attemptId,
            text,
            AutoDialTaskType.SURVEY.name(),
            confirmDigit,
            timeoutSeconds
        );
    }

    /**
     * 分派固定外呼模型的最终指令结果；保持会话锁和终态保护。
     * @param call 当前通话
     * @param params 规范指令结果
     * @return 是否由外呼模型处理
     */
    public boolean commandResult(CallInfoBO call, JsonNode params) {
        String template = call.getDataStr("runtimeTemplate", "");
        if (!templates().contains(template)) return false;
        synchronized (call) {
            if (call.getData().containsKey(DATA_TERMINAL)) return true;
            if (isAgentCall(template)) {
                postCall.commandResult(call, params);
                return true;
            }
            return notification.commandResult(call, params);
        }
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

        var riskDecision = admissionService.checkDestinationRisk(number);
        if (!riskDecision.admitted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, riskDecision.reasonMessage());
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
        media.dial(call, true);
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
        if (number.isBlank() || "0000".equals(number) || extension.isBlank()) {
            return false;
        }
        // 如果拨打的是系统配置的呼入 DID，必须交由呼入流程处理，非坐席主动外呼
        if (!agents.inbound(number, "default").isEmpty()) {
            return false;
        }
        return true;
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
        // 1. 坐席归属与在岗登录态强校验 (防未登录工位盗打)
        var agentDecision = admissionService.checkAgentAdmission(extension);
        if (!agentDecision.admitted()) {
            log.warn("🛡️ [准入拦截] 坐席准入拒绝 extension={}, reason={}", extension, agentDecision.reasonMessage());
            persistence.recordRejectedCall(controlId, extension, number, null, agentDecision.reasonCode(), agentDecision.reasonMessage());
            client.hangup(controlId, channelUuid, "CALL_REJECTED");
            return null;
        }
        Map<String, Object> agent = agentDecision.agentData();
        String owner = agentDecision.workNo();

        // 2. 目标号码防盗打风控过滤 (防国际长途与高危号段)
        var riskDecision = admissionService.checkDestinationRisk(number);
        if (!riskDecision.admitted()) {
            log.warn("🛡️ [防盗打拦截] 号码风控拦截 number={}, reason={}", number, riskDecision.reasonMessage());
            persistence.recordRejectedCall(controlId, extension, number, owner, riskDecision.reasonCode(), riskDecision.reasonMessage());
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

            media.persistLeg(
                call,
                channelUuid,
                state,
                params.path(FccEventField.CAUSE.getWireName()).asText(null)
            );
            media.updateAgentWorkStatus(call, eventState, channelUuid);
            boolean answeredExplicitlyFalse = params.has("answered") && !params.path("answered").asBoolean();
            if (TEMPLATE_NOTIFICATION.equals(template) && (eventState == ChannelEventState.ANSWERED || (eventState == ChannelEventState.READY && !answeredExplicitlyFalse))) {
                notification.startNotificationPrompt(call);
            } else if (
                (eventState == ChannelEventState.READY || eventState == ChannelEventState.ANSWERED) &&
                channelUuid.equals(call.getAgentChannelUuid())
            ) {
                boolean answered = params.path("answered").asBoolean(eventState == ChannelEventState.ANSWERED);
                if (answered || TEMPLATE_AGENT_ORIGINATED.equals(template)) {
                    media.startCustomerLeg(call);
                }
            } else if (
                (eventState == ChannelEventState.READY || eventState == ChannelEventState.ANSWERED) &&
                channelUuid.equals(call.getGuestChannelUuid())
            ) {
                media.bridgeAgentAndCustomer(call);
            } else if (eventState == ChannelEventState.BRIDGE) {
                media.markConnected(call);
            } else if (eventState == ChannelEventState.DESTROY) {
                if (
                    isAgentCall(template) &&
                    call.getStageState() == CallStageState.CONNECTED &&
                    channelUuid.equals(call.getAgentChannelUuid()) &&
                    !channelUuid.equals(call.getGuestChannelUuid())
                ) {
                    if (Boolean.TRUE.equals(call.getData().get("transferPending")) ||
                        Boolean.TRUE.equals(call.getData().get("transferAborted"))) {
                        log.info("[外呼事件] 通话处于转接中或转接异常，跳过服务评价 callId={}", call.getCallId());
                    } else {
                        lifecycle.beginPostCall(call, template, params.path(FccEventField.CAUSE.getWireName()).asText());
                    }
                } else {
                    lifecycle.finish(call, template, channelUuid, params);
                }
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
     * 判断模板是否包含需要占用和释放坐席的双向人工外呼。
     *
     * @param template 固定运行模板代码
     * @return 两类人工外呼返回 {@code true}
     */
    private boolean isAgentCall(String template) {
        return TEMPLATE_AGENT_FIRST.equals(template) || TEMPLATE_AGENT_ORIGINATED.equals(template);
    }
}
