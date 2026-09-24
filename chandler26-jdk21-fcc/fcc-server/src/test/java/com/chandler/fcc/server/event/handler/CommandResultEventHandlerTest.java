package com.chandler.fcc.server.event.handler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.server.agent.application.PhoneBindingService;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 验证异步命令完成事实的审计、关联和可重试失败边界。
 */
class CommandResultEventHandlerTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CallSessionManager sessions = mock(CallSessionManager.class);
    private final CallPersistenceService persistence = mock(CallPersistenceService.class);
    private final PhoneBindingService binding = mock(PhoneBindingService.class);
    private final InboundCallService inbound = mock(InboundCallService.class);
    private final OutboundCallService outbound = mock(OutboundCallService.class);
    private final CommandResultEventHandler handler = new CommandResultEventHandler(
        sessions, persistence, binding, inbound, outbound
    );

    /**
     * 指令结果先落审计，再交给活跃通话的绑定流程。
     *
     * @throws Exception 测试 JSON 解析失败
     */
    @Test
    void persistsAndDispatchesBindingResult() throws Exception {
        JsonNode event = result("SUCCEEDED");
        CallInfoBO call = CallInfoBO.builder()
            .callId("9001")
            .ctrlId("ctrl-9001")
            .guestChannelUuid("binding-channel")
            .data(new HashMap<>())
            .build();
        when(persistence.completeCommand(
            eq("binding-dtmf-9001"), eq("FNode.ReadDTMF"), eq("node-a"),
            eq("ctrl-9001"), eq("binding-channel"),
            eq("SUCCESS"), eq(null), eq("OK"), any(String.class)
        )).thenReturn(1);
        when(sessions.getByCtrlUuid("ctrl-9001")).thenReturn(Optional.of(call));
        when(binding.commandResult(call, event)).thenReturn(true);

        assertTrue(handler.supports(FccEventMethod.COMMAND_RESULT));
        handler.handle(event);

        verify(binding).commandResult(call, event);
        verify(inbound, never()).commandResult(any(), any());
    }

    /**
     * 审计缺失不能确认事件，JetStream 应等待同一事件重投。
     *
     * @throws Exception 测试 JSON 解析失败
     */
    @Test
    void retriesWhenCommandIntentIsMissing() throws Exception {
        JsonNode event = result("SUCCEEDED");
        assertThrows(IllegalStateException.class, () -> handler.handle(event));
        verify(binding, never()).commandResult(any(), any());
    }

    /**
     * 活跃通话已经离开内存时仍保存命令最终审计，不重新执行业务副作用。
     *
     * @throws Exception 测试 JSON 解析失败
     */
    @Test
    void auditsLateResultWithoutActiveCall() throws Exception {
        JsonNode event = result("FAILED");
        when(persistence.completeCommand(
            eq("binding-dtmf-9001"), eq("FNode.ReadDTMF"), eq("node-a"),
            eq("ctrl-9001"), eq("binding-channel"),
            eq("FAILED"), eq("-32004"), eq("OK"), any(String.class)
        )).thenReturn(1);
        when(sessions.getByCtrlUuid("ctrl-9001")).thenReturn(Optional.empty());
        when(sessions.getByChannelUuid("binding-channel")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(binding, never()).commandResult(any(), any());
    }

    /**
     * 创建带稳定身份和收号结果的规范事件参数。
     *
     * @param status 命令最终状态
     * @return JSON 事件参数
     * @throws Exception 测试 JSON 解析失败
     */
    private JsonNode result(String status) throws Exception {
        return json.readTree(
            "{\"event_id\":\"" + "a".repeat(64) +
            "\",\"node_id\":\"node-a\",\"ctrl_uuid\":\"ctrl-9001\"," +
            "\"uuid\":\"binding-channel\",\"command_id\":\"binding-dtmf-9001\"," +
            "\"command_method\":\"FNode.ReadDTMF\",\"command_status\":\"" + status +
            "\",\"code\":-32004,\"message\":\"OK\",\"result\":{\"dtmf\":\"901001\"}}"
        );
    }
}
