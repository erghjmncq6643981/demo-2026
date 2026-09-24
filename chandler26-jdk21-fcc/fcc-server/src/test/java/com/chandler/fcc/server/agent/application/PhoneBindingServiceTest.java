package com.chandler.fcc.server.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.command.FNodeAnswerDTO;
import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.chandler.fcc.server.agent.infrastructure.PhoneBindingMapper;
import com.chandler.fcc.server.event.handler.DtmfEventHandler;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.application.executor.FlowActionResult;
import com.chandler.fcc.server.flow.application.executor.FlowActionStatus;
import com.chandler.fcc.server.flow.application.executor.InternalFlowActionInvocation;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 话机拨号绑定可信入口、TEXT 收号和完整工号处理测试。
 */
class PhoneBindingServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private PhoneBindingMapper mapper;
    private TransactionTemplate transactions;
    private FlowActionExecutionService actions;
    private CallPersistenceService persistence;
    private FlowConfig flowConfig;
    private PhoneBindingService service;

    /**
     * 创建不启动 Spring 容器的业务服务测试夹具。
     */
    @BeforeEach
    void setUp() {
        mapper = mock(PhoneBindingMapper.class);
        transactions = mock(TransactionTemplate.class);
        actions = mock(FlowActionExecutionService.class);
        persistence = mock(CallPersistenceService.class);
        flowConfig = mock(FlowConfig.class);
        service = new PhoneBindingService(
            mapper,
            transactions,
            actions,
            persistence,
            flowConfig,
            json
        );
        ReflectionTestUtils.setField(service, "minWorkNoDigits", 2);
        ReflectionTestUtils.setField(service, "maxWorkNoDigits", 20);
        when(actions.executeInternal(any(), any(), any())).thenAnswer(invocation -> {
            InternalFlowActionInvocation businessAction = invocation.getArgument(2);
            Object output = businessAction.invoke();
            return FlowActionResult.builder()
                .status(FlowActionStatus.SUCCEEDED)
                .output(output)
                .build();
        });
        when(actions.executeFNode(any(), any(), any(), any())).thenReturn(
            FlowActionResult.builder().status(FlowActionStatus.ACCEPTED).build()
        );
    }

    /**
     * CHANNEL_CREATE 发生在 dialplan set 之前，不能提前校验或持久化绑定事实。
     */
    @Test
    void waitsForReadyEventBeforeInitializingBinding() {
        CallInfoBO call = bindingCall();

        assertTrue(service.channel(call, channelEvent("START", null, null)));

        verify(mapper, never()).bindingContext(any());
        verify(persistence, never()).saveOrUpdateSession(any());
        verify(actions, never()).executeFNode(any(), any(), any(), any());
        assertFalse(call.getData().containsKey("bindingInitialized"));
    }

    /**
     * READY 只应答可信话道，真正 ANSWERED 后才播放提示并收号。
     */
    @Test
    void trustedReadyEventAnswersBeforeTextDigitCollection() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();

        assertTrue(
            service.channel(
                call,
                channelEvent("READY", "PHONE_BINDING", "1001")
            )
        );

        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.ANSWER_BINDING_CHANNEL),
            any(FNodeAnswerDTO.class),
            eq("binding-answer-9001")
        );
        verify(actions, never()).executeFNode(
            eq(call), eq(FlowActionType.READ_DTMF), any(), any()
        );
        assertTrue(service.channel(call, channelEvent("ANSWERED", null, null)));

        ArgumentCaptor<FNodeReadDTMFDTO> command = ArgumentCaptor.forClass(
            FNodeReadDTMFDTO.class
        );
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.READ_DTMF),
            command.capture(),
            eq("binding-dtmf-9001")
        );
        assertEquals(FNodeMediaType.TEXT, command.getValue().getMedia().getType());
        assertEquals(
            "请输入您的坐席工号，输入完成后请按井号键。",
            command.getValue().getMedia().getData()
        );
        assertEquals("PHONE_BINDING", call.getDataStr("runtimeTemplate", null));
        assertEquals("1001", call.getDataStr("bindingExtension", null));
        service.channel(call, channelEvent("READY", "PHONE_BINDING", "1001"));
        service.channel(call, channelEvent("ANSWERED", null, null));
        verify(actions).executeFNode(
            eq(call), eq(FlowActionType.READ_DTMF), any(), eq("binding-dtmf-9001")
        );
    }

    /**
     * 应答同步结果未知时，先持久化的发起标记必须允许提前到达的 ANSWERED 继续收号。
     */
    @Test
    void continuesWhenAnsweredArrivesBeforeAnswerAcknowledgement() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();
        when(
            actions.executeFNode(
                eq(call),
                eq(FlowActionType.ANSWER_BINDING_CHANNEL),
                any(FNodeAnswerDTO.class),
                eq("binding-answer-9001")
            )
        ).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(
            ResponseStatusException.class,
            () -> service.channel(
                call,
                channelEvent("READY", "PHONE_BINDING", "1001")
            )
        );
        assertTrue(call.getData().containsKey("bindingAnswerRequested"));
        when(actions.executeFNode(any(), any(), any(), any())).thenReturn(
            FlowActionResult.builder().status(FlowActionStatus.ACCEPTED).build()
        );

        assertTrue(service.channel(call, channelEvent("ANSWERED", null, null)));

        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.READ_DTMF),
            any(FNodeReadDTMFDTO.class),
            eq("binding-dtmf-9001")
        );
    }

    /**
     * 仅被叫号码为 0000 不构成可信入口，必须拒绝且不得查询或写入绑定事实。
     */
    @Test
    void rejectsReadyEventWithoutDialplanMarker() {
        CallInfoBO call = bindingCall();

        assertTrue(service.channel(call, channelEvent("READY", null, "1001")));

        verify(mapper, never()).bindingContext(any());
        verify(persistence, never()).saveOrUpdateSession(any());
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.HANGUP_BINDING_CHANNEL),
            any(FNodeHangupDTO.class),
            eq("binding-hangup-9001")
        );
        assertFalse(call.getData().containsKey("bindingInitialized"));
    }

    /**
     * dialplan 标记不能替代 SIP 认证身份，缺少认证分机时仍然拒绝绑定。
     */
    @Test
    void rejectsTrustedEntryWithoutAuthenticatedExtension() {
        CallInfoBO call = bindingCall();

        assertTrue(
            service.channel(
                call,
                channelEvent("READY", "PHONE_BINDING", null)
            )
        );

        verify(mapper, never()).bindingContext(any());
        verify(persistence, never()).saveOrUpdateSession(any());
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.HANGUP_BINDING_CHANNEL),
            any(FNodeHangupDTO.class),
            eq("binding-hangup-9001")
        );
        assertFalse(call.getData().containsKey("bindingInitialized"));
    }

    /**
     * 逐键 DTMF 只用于观测，不能把首个按键误认为完整工号执行换绑。
     */
    @Test
    void ignoresRawKeyPressDuringDigitCollection() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();
        service.channel(call, channelEvent("READY", "PHONE_BINDING", "1001"));
        clearInvocations(mapper, actions, persistence);

        new DtmfEventHandler().handle(dtmfEvent("9", "KEY_PRESS"));

        verify(mapper, never()).lockBindingTarget(any(), any());
        verify(persistence, never()).saveOrUpdateSession(any());
        verify(actions, never()).executeFNode(any(), any(), any(), any());
        assertFalse(call.getData().containsKey("bindingCompleted"));
    }

    /**
     * 完整工号不合法时不访问绑定 SQL，播放失败语音完成后才单独挂机。
     */
    @Test
    void playsFailureResultForInvalidCollectedDigits() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();
        service.channel(call, channelEvent("READY", "PHONE_BINDING", "1001"));
        service.channel(call, channelEvent("ANSWERED", null, null));
        clearInvocations(mapper, actions, persistence);

        assertTrue(service.commandResult(call, commandResult("binding-dtmf-9001", "SUCCEEDED", "1")));

        verify(mapper, never()).lockBindingTarget(any(), any());
        ArgumentCaptor<FNodePlayDTO> resultCommand = ArgumentCaptor.forClass(
            FNodePlayDTO.class
        );
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.PLAY_BINDING_RESULT),
            resultCommand.capture(),
            eq("binding-result-9001")
        );
        assertEquals(
            "话机绑定失败，请确认工号后重试。",
            resultCommand.getValue().getMedia().getData()
        );
        assertEquals(FNodePlayPostAction.PARK, resultCommand.getValue().getActionAfter());
        assertEquals(Boolean.FALSE, call.getData().get("bindingAccepted"));
        verify(actions, never()).executeFNode(
            eq(call), eq(FlowActionType.HANGUP_BINDING_CHANNEL), any(), any()
        );
        assertTrue(service.commandResult(call, commandResult("binding-result-9001", "SUCCEEDED", null)));
        verify(actions).executeFNode(
            eq(call), eq(FlowActionType.HANGUP_BINDING_CHANNEL),
            any(FNodeHangupDTO.class), eq("binding-hangup-9001")
        );
    }

    /**
     * 结果语音同步 ACK 未知时，事件重投必须复用原 command_id 补查，不能重复换绑。
     */
    @Test
    void retriesResultPlaybackWithSameCommandAfterUnknownAcknowledgement() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();
        service.channel(call, channelEvent("READY", "PHONE_BINDING", "1001"));
        service.channel(call, channelEvent("ANSWERED", null, null));
        when(
            actions.executeFNode(
                eq(call),
                eq(FlowActionType.PLAY_BINDING_RESULT),
                any(FNodePlayDTO.class),
                eq("binding-result-9001")
            )
        ).thenThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT));

        assertThrows(
            ResponseStatusException.class,
            () -> service.commandResult(
                call,
                commandResult("binding-dtmf-9001", "SUCCEEDED", "1")
            )
        );
        assertTrue(call.getData().containsKey("bindingCompleted"));
        assertTrue(call.getData().containsKey("bindingResultRequested"));
        assertFalse(call.getData().containsKey("bindingResultSent"));
        when(actions.executeFNode(any(), any(), any(), any())).thenReturn(
            FlowActionResult.builder().status(FlowActionStatus.ACCEPTED).build()
        );

        assertTrue(service.commandResult(
            call,
            commandResult("binding-dtmf-9001", "SUCCEEDED", "1")
        ));

        verify(actions, times(2)).executeFNode(
            eq(call),
            eq(FlowActionType.PLAY_BINDING_RESULT),
            any(FNodePlayDTO.class),
            eq("binding-result-9001")
        );
        verify(mapper, never()).lockBindingTarget(any(), any());
    }

    /**
     * 收号应用返回完整工号后才执行原子换绑、保存结果并挂机。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bindsOnlyCollectedDigits() {
        CallInfoBO call = bindingCall();
        prepareTrustedEntry();
        service.channel(call, channelEvent("READY", "PHONE_BINDING", "1001"));
        service.channel(call, channelEvent("ANSWERED", null, null));
        clearInvocations(mapper, actions, persistence);
        when(mapper.lockBindingTarget("1001", "901001")).thenReturn(Map.of("agentId", 1L));
        when(mapper.busy("901001", "1001")).thenReturn(0);
        when(mapper.bindExtension("901001", "1001")).thenReturn(1);
        when(mapper.appendBinding(anyLong(), eq("901001"), eq("1001"))).thenReturn(1);
        when(
            mapper.appendSelectionAudit(anyLong(), eq("901001"), eq(null), anyLong())
        ).thenReturn(1);
        when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        assertTrue(service.commandResult(
            call, commandResult("binding-dtmf-9001", "SUCCEEDED", "901001")
        ));

        verify(mapper).lockBindingTarget("1001", "901001");
        verify(mapper).bindExtension("901001", "1001");
        ArgumentCaptor<FNodePlayDTO> resultCommand = ArgumentCaptor.forClass(
            FNodePlayDTO.class
        );
        verify(actions).executeFNode(
            eq(call),
            eq(FlowActionType.PLAY_BINDING_RESULT),
            resultCommand.capture(),
            eq("binding-result-9001")
        );
        assertEquals(FNodeMediaType.TEXT, resultCommand.getValue().getMedia().getType());
        assertEquals("话机绑定成功，再见。", resultCommand.getValue().getMedia().getData());
        assertEquals(FNodePlayPostAction.PARK, resultCommand.getValue().getActionAfter());
        assertEquals(Boolean.TRUE, call.getData().get("bindingAccepted"));
        service.commandResult(call, commandResult("binding-dtmf-9001", "SUCCEEDED", "901001"));
        verify(mapper).bindExtension("901001", "1001");
        service.commandResult(call, commandResult("binding-result-9001", "FAILED", null));
        service.commandResult(call, commandResult("binding-result-9001", "FAILED", null));
        verify(actions).executeFNode(
            eq(call), eq(FlowActionType.HANGUP_BINDING_CHANNEL),
            any(FNodeHangupDTO.class), eq("binding-hangup-9001")
        );
    }

    /**
     * 创建用于绑定的内存通话上下文。
     *
     * @return 绑定通话
     */
    private CallInfoBO bindingCall() {
        return CallInfoBO.builder()
            .callId("9001")
            .ctrlId("ctrl-9001")
            .guestChannelUuid("binding-channel")
            .destinationNumber("0000")
            .data(new HashMap<>())
            .build();
    }

    /**
     * 准备启用分机和已发布的系统绑定模型。
     */
    private void prepareTrustedEntry() {
        FlowConfig.FlowSnapshot flow = mock(FlowConfig.FlowSnapshot.class);
        when(flow.definitionId()).thenReturn("definition-1");
        when(flow.versionId()).thenReturn("version-1");
        when(flow.definitionJson()).thenReturn(
            """
            {
              "parameters":{"promptText":"请输入您的坐席工号，输入完成后请按井号键。"},
              "nodes":[{"key":"RESULT","parameters":{"successText":"话机绑定成功，再见。","failureText":"话机绑定失败，请确认工号后重试。"}}]
            }
            """
        );
        when(mapper.bindingContext("1001")).thenReturn(Map.of("extensionId", 1L));
        when(flowConfig.getPublishedFlow(null, "PHONE_BINDING")).thenReturn(
            Optional.of(flow)
        );
    }

    /**
     * 创建标准 Channel 事件参数。
     *
     * @param state 通道状态
     * @param flowEntry dialplan 入口标记，可为空
     * @param extension SIP 已认证分机，可为空
     * @return Channel 事件参数
     */
    private ObjectNode channelEvent(String state, String flowEntry, String extension) {
        ObjectNode event = json.createObjectNode();
        event.put("state", state);
        ObjectNode parameters = event.putObject("params");
        if (flowEntry != null) {
            parameters.put("flow_entry", flowEntry);
        }
        if (extension != null) {
            parameters.put("authenticated_extension", extension);
        }
        return event;
    }

    /**
     * 创建带来源类型的标准 DTMF 事件参数。
     *
     * @param digits 单个物理按键
     * @param source 事件来源
     * @return DTMF 事件参数
     */
    private ObjectNode dtmfEvent(String digits, String source) {
        ObjectNode event = json.createObjectNode();
        event.put("uuid", "binding-channel");
        event.put("digit", digits);
        event.put("source", source);
        return event;
    }

    /**
     * 创建 Sidecar 指令完成事件；业务收号放在 result.dtmf 而非 Event.DTMF。
     *
     * @param commandId 稳定指令标识
     * @param status 最终执行状态
     * @param digits 完整收号，可为空
     * @return 规范指令结果参数
     */
    private ObjectNode commandResult(String commandId, String status, String digits) {
        ObjectNode event = json.createObjectNode();
        event.put("command_id", commandId);
        event.put("command_status", status);
        if (digits != null) {
            event.putObject("result").put("dtmf", digits);
        }
        return event;
    }
}
