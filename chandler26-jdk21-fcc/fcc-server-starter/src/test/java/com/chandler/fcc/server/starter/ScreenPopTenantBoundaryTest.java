package com.chandler.fcc.server.starter;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.websocket.handler.AgentWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 同工号跨租户长连接不能接收另一租户的话务弹屏。 */
class ScreenPopTenantBoundaryTest {
    /** 同时在线的同名坐席只向通话所属租户投递。 */
    @Test void routesOnlyToCallTenant() throws Exception {
        var identity=mock(AgentIdentityService.class);
        var calls=mock(CallSessionManager.class);
        var handler=new AgentWebSocketHandler();
        ReflectionTestUtils.setField(handler,"identity",identity);
        ReflectionTestUtils.setField(handler,"callSessions",calls);
        ReflectionTestUtils.setField(handler,"deliveries",mock(com.chandler.fcc.server.websocket.service.ScreenPopDeliveryStore.class));
        var local=session(42,"local-token");
        var foreign=session(43,"foreign-token");
        when(identity.authenticatePrincipal("local-token")).thenReturn(new AgentIdentityService.Principal("alice",42));
        when(identity.authenticatePrincipal("foreign-token")).thenReturn(new AgentIdentityService.Principal("alice",43));
        when(calls.getByCallId("call-test")).thenReturn(Optional.of(CallInfoBO.builder().callId("call-test").data(new HashMap<>(Map.of("tenantId",42L))).build()));
        handler.afterConnectionEstablished(local);
        handler.afterConnectionEstablished(foreign);
        clearInvocations(local,foreign);
        assertEquals(1,handler.sendToWorkNo("alice",WsMessageDTO.of("CALL_SCREEN_POP","alice","call-test",Map.of())));
        verify(local).sendMessage(any());
        verify(foreign,never()).sendMessage(any());
        assertEquals(0,handler.sendToWorkNo("alice",WsMessageDTO.of("CALL_SCREEN_POP","alice",null,Map.of())));
    }

    /** 建立仅供测试的认证属性，不包含真实凭据。
     * @param tenant 测试租户
     * @param token 测试令牌
     * @return 模拟长连接
     */
    private WebSocketSession session(long tenant,String token) {
        var session=mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of("WORK_NO","alice","TENANT_ID",tenant,"AUTH_TOKEN",token)));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
