package com.chandler.fcc.server.telephony.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.application.executor.FlowActionResult;
import com.chandler.fcc.server.flow.application.executor.FlowActionStatus;
import com.chandler.fcc.server.flow.application.executor.InternalFlowActionInvocation;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.outbound.application.DialAttemptGuard;
import com.chandler.fcc.server.recording.application.CallRecordingService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 人工外呼两个入口的运行边界测试。
 */
class OutboundCallServiceTest {

    private final AgentRuntimeMapper agents = mock(AgentRuntimeMapper.class);
    private final OutboundRoutePolicy routes = mock(OutboundRoutePolicy.class);
    private final CallSessionManager sessions = mock(CallSessionManager.class);
    private final CallPersistenceService persistence = mock(CallPersistenceService.class);
    private final FlowActionExecutionService flowActions = mock(FlowActionExecutionService.class);
    private final ScreenPopService screenPop = mock(ScreenPopService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final FccClient client = mock(FccClient.class);
    private final FlowConfig flowConfig = mock(FlowConfig.class);
    private FlowConfig.FlowSnapshot snapshot;

    private OutboundCallService service;

    /**
     * 构造无需外部基础设施的动作与事务执行环境。
     */
    @BeforeEach
    void setUp() {
        snapshot = mock(FlowConfig.FlowSnapshot.class);
        when(snapshot.definitionId()).thenReturn("flow-definition");
        when(snapshot.versionId()).thenReturn("flow-version");
        when(snapshot.modelType()).thenReturn("NOTIFICATION");
        when(snapshot.definitionJson()).thenReturn("""
            {
              "routeMode":"AUTO_DIAL",
              "template":"NOTIFICATION",
              "notification":{"text":"发布版本通知","confirmDigit":"2","timeoutSeconds":15},
              "stages":["ENTRY","DIAL_CUSTOMER","NOTIFY","CONFIRM","END"]
            }
            """);
        when(flowConfig.getPublishedFlow(any(), any())).thenReturn(Optional.of(snapshot));
        when(flowActions.executeInternal(any(), any(), any())).thenAnswer(invocation -> {
            InternalFlowActionInvocation action = invocation.getArgument(2);
            Object output = action.invoke();
            return FlowActionResult.builder()
                .status(FlowActionStatus.SUCCEEDED)
                .output(output)
                .build();
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());

        service = new OutboundCallService(
            mock(AgentIdentityService.class),
            agents,
            routes,
            sessions,
            persistence,
            client,
            flowActions,
            flowConfig,
            screenPop,
            mock(AgentWebSocketService.class),
            transactions,
            mock(DialAttemptGuard.class),
            mock(CallRecordingService.class),
            mock(InboundPostCallService.class)
        );
    }

    /**
     * 自动外呼必须固定系统通知模型，并使用本次任务携带的通知参数。
     */
    @Test
    void startsNotificationFromTaskParameters() {
        when(routes.resolve("13800000000")).thenReturn(
            new OutboundRoutePolicy.Route("13800000000", "mobile", "4000000000")
        );

        service.startAutoDial("13800000000", "attempt-1", "本次任务通知", "8", 12);

        ArgumentCaptor<CallInfoBO> call = ArgumentCaptor.forClass(CallInfoBO.class);
        verify(sessions).registerSession(call.capture());
        assertEquals("本次任务通知", call.getValue().getDataStr("notificationText", null));
        assertEquals("8", call.getValue().getDataStr("confirmDigit", null));
        assertEquals(12, call.getValue().getData().get("notificationTimeoutSeconds"));
        assertEquals("SYSTEM_NOTIFICATION", call.getValue().getDataStr("flowKey", null));
        assertEquals("flow-version", call.getValue().getDataStr("flowVersionId", null));
        verify(flowConfig).getPublishedFlow(null, "SYSTEM_NOTIFICATION");
    }

    /**
     * 自动外呼不能接受缺失文案或非法确认参数。
     */
    @Test
    void rejectsInvalidNotificationParameters() {
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> service.startAutoDial("13800000000", "attempt-1", " ", "8", 12)
        );
        verify(routes, never()).resolve(any());
    }

    /**
     * 已认证终端主动拨号应接管现有坐席 Leg，并只向客户发送 Dial。
     */
    @Test
    void acceptsAuthenticatedAgentOriginatedCallAndDialsCustomer() {
        when(agents.agentByEndpoint("1001")).thenReturn(
            Map.of("workNo", "901001", "endpointType", "SIP", "extension", "1001")
        );
        when(agents.reserveOriginated(eq("901001"), any())).thenReturn(1);
        when(routes.resolve("13800000000")).thenReturn(
            new OutboundRoutePolicy.Route("13800000000", "mobile", "4000000000")
        );

        ObjectNode start = channelEvent("START", "agent-channel", "13800000000");
        start.put("direction", "inbound");
        start.put("context", "default");
        start.putObject("params").put("authenticated_extension", "1001");

        CallInfoBO call = service.createAgentOriginated(
            start,
            "node-a",
            ChannelEventState.START,
            "agent-channel",
            null
        );

        assertNotNull(call);
        assertEquals("AGENT_ORIGINATED", call.getDataStr("runtimeTemplate", null));
        assertEquals("agent-channel", call.getAgentChannelUuid());
        assertEquals("13800000000", call.getDestinationNumber());
        verify(agents).reserveOriginated(eq("901001"), eq(call.getCallId()));
        verify(flowActions, never()).executeFNode(
            any(),
            eq(FlowActionType.DIAL_AGENT),
            any(),
            any()
        );

        ObjectNode ready = channelEvent("READY", "agent-channel", "13800000000");
        service.event(call, ready);

        ArgumentCaptor<FNodeDialDTO> command = ArgumentCaptor.forClass(FNodeDialDTO.class);
        verify(flowActions).executeFNode(
            eq(call),
            eq(FlowActionType.DIAL_CUSTOMER),
            command.capture(),
            eq("dial-" + call.getGuestChannelUuid())
        );
        FNodeDialDTO.CallParam destination = command
            .getValue()
            .getDestination()
            .getCallParams()
            .getFirst();
        assertEquals("13800000000", destination.getDialString());
        assertEquals("mobile", destination.getContext());
    }

    /**
     * 普通运营商呼入没有认证分机时不能被误识别为坐席主动外呼。
     */
    @Test
    void ignoresInboundCallWithoutAuthenticatedExtension() {
        ObjectNode start = channelEvent("START", "customer-channel", "4000000000");
        start.put("direction", "inbound");
        start.put("context", "telecom");

        assertNull(
            service.createAgentOriginated(
                start,
                "node-a",
                ChannelEventState.START,
                "customer-channel",
                null
            )
        );
        verify(agents, never()).agentByEndpoint(any());
    }

    /**
     * 已认证但尚未绑定坐席的终端必须被拒绝，不能落入普通呼入模型。
     */
    @Test
    void rejectsAuthenticatedButUnboundExtension() {
        ObjectNode start = channelEvent("START", "agent-channel", "13800000000");
        start.put("direction", "inbound");
        start.put("context", "default");
        start.putObject("params").put("authenticated_extension", "1009");

        assertTrue(service.isAgentOriginatedEntry(start, ChannelEventState.START));
        assertNull(
            service.createAgentOriginated(
                start,
                "node-a",
                ChannelEventState.START,
                "agent-channel",
                "ctrl-a"
            )
        );
        verify(client).hangup("ctrl-a", "agent-channel", "CALL_REJECTED");
        verify(routes, never()).resolve(any());
    }

    /**
     * 坐席已被其他通话占用时应按业务拒绝挂机，不应让事件进入重试。
     */
    @Test
    void rejectsBusyAgentOriginatedCallWithoutRetryableFailure() {
        when(agents.agentByEndpoint("1001")).thenReturn(
            Map.of("workNo", "901001", "endpointType", "SIP", "extension", "1001")
        );
        when(agents.reserveOriginated(eq("901001"), any())).thenReturn(0);
        when(routes.resolve("13800000000")).thenReturn(
            new OutboundRoutePolicy.Route("13800000000", "mobile", "4000000000")
        );

        ObjectNode start = channelEvent("START", "agent-channel", "13800000000");
        start.put("direction", "inbound");
        start.put("context", "default");
        start.putObject("params").put("authenticated_extension", "1001");

        assertNull(
            service.createAgentOriginated(
                start,
                "node-a",
                ChannelEventState.START,
                "agent-channel",
                "ctrl-a"
            )
        );
        verify(client).hangup("ctrl-a", "agent-channel", "USER_BUSY");
        verify(sessions, never()).registerSession(any());
    }

    /**
     * 创建最小规范 Channel 事件。
     *
     * @param state 话道状态
     * @param channelUuid 话道标识
     * @param destinationNumber 被叫号码
     * @return 事件参数对象
     */
    private ObjectNode channelEvent(
        String state,
        String channelUuid,
        String destinationNumber
    ) {
        ObjectNode event = json.createObjectNode();
        event.put("state", state);
        event.put("uuid", channelUuid);
        event.put("dest_number", destinationNumber);
        return event;
    }
}
