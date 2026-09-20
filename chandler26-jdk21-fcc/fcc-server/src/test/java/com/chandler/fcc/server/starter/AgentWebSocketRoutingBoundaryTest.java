package com.chandler.fcc.server.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.websocket.handler.AgentWebSocketHandler;
import com.chandler.fcc.server.websocket.service.ScreenPopDeliveryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

/**
 * 验证实时消息只投递到服务端认证出的目标坐席。
 */
class AgentWebSocketRoutingBoundaryTest {

    /**
     * 两个不同坐席同时在线时，弹屏不得发送给非目标坐席。
     *
     * @throws Exception 建立模拟会话或发送消息失败
     */
    @Test
    void routesOnlyToTargetAgent() throws Exception {
        AgentIdentityService identity = mock(AgentIdentityService.class);
        ScreenPopDeliveryStore deliveries = mock(ScreenPopDeliveryStore.class);
        AgentWebSocketHandler handler = new AgentWebSocketHandler(
            identity,
            deliveries,
            new ObjectMapper()
        );
        WebSocketSession alice = session("alice", "alice-token");
        WebSocketSession bob = session("bob", "bob-token");
        when(identity.authenticatePrincipal("alice-token")).thenReturn(
            new AgentIdentityService.Principal("alice")
        );
        when(identity.authenticatePrincipal("bob-token")).thenReturn(
            new AgentIdentityService.Principal("bob")
        );

        handler.afterConnectionEstablished(alice);
        handler.afterConnectionEstablished(bob);
        clearInvocations(alice, bob);

        int delivered = handler.sendToWorkNo(
            "alice",
            WsMessageDTO.of("CALL_SCREEN_POP", "alice", "900001", Map.of())
        );

        assertEquals(1, delivered);
        verify(alice).sendMessage(any());
        verify(bob, never()).sendMessage(any());
    }

    /**
     * 创建仅包含服务端握手属性的模拟连接。
     *
     * @param workNo 已认证坐席工号
     * @param token 测试令牌
     * @return 可发送消息的模拟连接
     */
    private WebSocketSession session(String workNo, String token) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(
            new HashMap<>(Map.of("WORK_NO", workNo, "AUTH_TOKEN", token))
        );
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
