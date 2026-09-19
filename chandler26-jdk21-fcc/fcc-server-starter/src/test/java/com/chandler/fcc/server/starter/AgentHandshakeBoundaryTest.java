package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.websocket.config.AgentHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** WebSocket 身份只能来自认证结果，不能来自 URL 工号。 */
class AgentHandshakeBoundaryTest {
    /** 有效令牌绑定身份；缺少令牌时即使存在查询工号也拒绝。 */
    @Test void bindsOnlyAuthenticatedIdentity() {
        var identity = mock(AgentIdentityService.class);
        var interceptor = new AgentHandshakeInterceptor(identity);
        var request = mock(ServerHttpRequest.class);
        var response = mock(ServerHttpResponse.class);
        var handler = mock(WebSocketHandler.class);
        var headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        var attributes = new HashMap<String, Object>();
        assertFalse(interceptor.beforeHandshake(request, response, handler, attributes));
        headers.set("Sec-WebSocket-Protocol", "fcc-agent, auth." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("test-token".getBytes(StandardCharsets.UTF_8)));
        when(identity.authenticate("test-token")).thenReturn("authenticated-agent");
        assertTrue(interceptor.beforeHandshake(request, response, handler, attributes));
        assertEquals("authenticated-agent", attributes.get("WORK_NO"));
    }
}
