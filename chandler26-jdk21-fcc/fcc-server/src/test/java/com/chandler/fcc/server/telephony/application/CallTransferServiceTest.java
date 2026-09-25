package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CallTransferServiceTest {

    private CallSessionManager sessions;
    private FccClient client;
    private CallPersistenceService persistence;
    private AgentWebSocketService websocket;
    private AgentRuntimeMapper agents;
    private CallTransferService transferService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessions = mock(CallSessionManager.class);
        client = mock(FccClient.class);
        persistence = mock(CallPersistenceService.class);
        websocket = mock(AgentWebSocketService.class);
        agents = mock(AgentRuntimeMapper.class);

        when(client.nativeAPI(anyString(), anyString())).thenReturn(FNodeResult.builder().code(200).build());
        when(client.dial(any())).thenReturn(FNodeResult.builder().code(200).build());
        when(client.channelBridge(anyString(), anyString(), anyString())).thenReturn(FNodeResult.builder().code(200).build());
        when(client.hangup(anyString(), anyString(), anyString())).thenReturn(FNodeResult.builder().code(200).build());

        transferService = new CallTransferService(sessions, client, persistence, websocket, agents);
    }

    @Test
    @DisplayName("测试发起咨询转接：A与B设为park并听回铃声，C发起30s超时呼叫")
    void testInitiateTransfer() {
        CallInfoBO call = CallInfoBO.builder()
            .callId("call-100")
            .ctrlId("ctrl-100")
            .stageState(CallStageState.CONNECTED)
            .agentChannelUuid("uuid-a-agent")
            .guestChannelUuid("uuid-b-guest")
            .agentWorkNo("901399")
            .data(new HashMap<>())
            .build();

        Map<String, Object> resp = transferService.initiateTransfer(call, "1017");

        assertEquals(200, resp.get("code"));
        assertTrue(Boolean.TRUE.equals(call.getData().get("transferPending")));
        assertEquals("1017", call.getData().get("transferTarget"));
        assertNotNull(call.getData().get("transferChannelUuid"));

        // 验证 uuid_answer 确保已应答
        verify(client).nativeAPI("uuid_answer", "uuid-a-agent");
        verify(client).nativeAPI("uuid_answer", "uuid-b-guest");

        // 验证 uuid_setvar 临时关闭 hangup_after_bridge
        verify(client).nativeAPI("uuid_setvar", "uuid-a-agent hangup_after_bridge false");
        verify(client).nativeAPI("uuid_setvar", "uuid-b-guest hangup_after_bridge false");

        // 验证 uuid_transfer 转入 park inline
        verify(client).nativeAPI("uuid_transfer", "uuid-a-agent -both park inline");
        verify(client).nativeAPI("uuid_transfer", "uuid-b-guest park inline");

        // 验证 A 和 B 广播放音听回铃
        verify(client).nativeAPI("uuid_broadcast", "uuid-a-agent tone_stream://%(2000,4000,440,480);loops=-1 aleg");
        verify(client).nativeAPI("uuid_broadcast", "uuid-b-guest tone_stream://%(2000,4000,440,480);loops=-1 aleg");

        // 验证呼叫 C (timeout=30)
        ArgumentCaptor<FNodeDialDTO> dialCaptor = ArgumentCaptor.forClass(FNodeDialDTO.class);
        verify(client).dial(dialCaptor.capture());
        assertEquals(30, dialCaptor.getValue().getTimeout());
        assertEquals("1017", dialCaptor.getValue().getDestination().getCallParams().getFirst().getDialString());
    }

    @Test
    @DisplayName("测试目标C接听：READY不触发转接成功，只有ANSWERED才桥接B与C并释放原坐席A至ACW")
    void testTargetAnswered() {
        CallInfoBO call = CallInfoBO.builder()
            .callId("100")
            .ctrlId("ctrl-100")
            .stageState(CallStageState.CONNECTED)
            .agentChannelUuid("uuid-a-agent")
            .guestChannelUuid("uuid-b-guest")
            .agentWorkNo("901399")
            .data(new HashMap<>())
            .build();

        transferService.initiateTransfer(call, "1017");
        String uuidC = (String) call.getData().get("transferChannelUuid");

        // 1. 模拟 C 话道刚建立并进入 park inline (READY, 但 answered=false)
        boolean readyHandled = transferService.handleChannelEvent(
            call,
            objectMapper.createObjectNode().put("answered", false),
            ChannelEventState.READY,
            uuidC
        );
        assertTrue(readyHandled);
        // 转接仍在进行中，坐席 A 绝不能被释放或进入 ACW！
        assertTrue(Boolean.TRUE.equals(call.getData().get("transferPending")));
        verify(agents, never()).release(anyString(), anyString());
        verify(client, never()).hangup(eq("ctrl-100"), eq("uuid-a-agent"), anyString());

        // 2. 模拟 C 真正摘机接听 (ANSWERED)
        boolean answeredHandled = transferService.handleChannelEvent(
            call,
            objectMapper.createObjectNode().put("answered", true),
            ChannelEventState.ANSWERED,
            uuidC
        );

        assertTrue(answeredHandled);
        assertFalse(Boolean.TRUE.equals(call.getData().get("transferPending")));
        assertTrue(Boolean.TRUE.equals(call.getData().get("transferSuccess")));

        // 验证停止回铃放音
        verify(client).nativeAPI("uuid_break", "uuid-a-agent all");
        verify(client).nativeAPI("uuid_break", "uuid-b-guest all");

        // 验证恢复 hangup_after_bridge
        verify(client).nativeAPI("uuid_setvar", "uuid-b-guest hangup_after_bridge true");
        verify(client).nativeAPI("uuid_setvar", uuidC + " hangup_after_bridge true");

        // 验证桥接 B 与 C
        verify(client).channelBridge("ctrl-100", "uuid-b-guest", uuidC);

        // 验证释放原坐席 A 并置为 ACW
        verify(agents).release("901399", "100");
        verify(client).hangup("ctrl-100", "uuid-a-agent", "NORMAL_CLEARING");
        verify(websocket).pushCallHangup(eq("901399"), eq("100"), any());
    }

    @Test
    @DisplayName("测试目标C超时未接听：停止回铃放音，重新桥接A与B，通知坐席")
    void testTargetTimeoutFallback() {
        CallInfoBO call = CallInfoBO.builder()
            .callId("call-100")
            .ctrlId("ctrl-100")
            .stageState(CallStageState.CONNECTED)
            .agentChannelUuid("uuid-a-agent")
            .guestChannelUuid("uuid-b-guest")
            .agentWorkNo("901399")
            .data(new HashMap<>())
            .build();

        transferService.initiateTransfer(call, "1017");
        String uuidC = (String) call.getData().get("transferChannelUuid");

        // 模拟 C 话道 30s 超时 DESTROY (NO_ANSWER)
        var params = objectMapper.createObjectNode();
        params.put("cause", "NO_ANSWER");

        boolean handled = transferService.handleChannelEvent(
            call,
            params,
            ChannelEventState.DESTROY,
            uuidC
        );

        assertTrue(handled);
        assertFalse(Boolean.TRUE.equals(call.getData().get("transferPending")));
        assertTrue(Boolean.TRUE.equals(call.getData().get("transferFailed")));

        // 验证停止回铃放音
        verify(client).nativeAPI("uuid_break", "uuid-a-agent all");
        verify(client).nativeAPI("uuid_break", "uuid-b-guest all");

        // 验证恢复 A 和 B 的 hangup_after_bridge
        verify(client).nativeAPI("uuid_setvar", "uuid-a-agent hangup_after_bridge true");
        verify(client).nativeAPI("uuid_setvar", "uuid-b-guest hangup_after_bridge true");

        // 验证重新桥接 A 与 B
        verify(client).channelBridge("ctrl-100", "uuid-a-agent", "uuid-b-guest");

        // 验证通知坐席通话恢复
        verify(websocket).pushCallAnswered(eq("901399"), eq("call-100"), any());
    }
}
