package com.chandler.fcc.server.websocket.config;

import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** 使用子协议头携带的登录令牌认证握手，URL 中不携带令牌或可伪造工号。 */
@Component
@RequiredArgsConstructor
public class AgentHandshakeInterceptor implements HandshakeInterceptor {
    private final AgentIdentityService identity;

    /** 验证认证子协议并绑定服务端身份。
     * @param request 握手请求
     * @param response 握手响应
     * @param handler WebSocket 处理器
     * @param attributes 服务端会话属性
     * @return 是否允许握手
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            String protocols = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
            if (protocols == null) throw new IllegalArgumentException();
            String encoded = java.util.Arrays.stream(protocols.split(",")).map(String::trim)
                    .filter(value -> value.startsWith("auth.")).findFirst().orElseThrow().substring(5);
            String token = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            var actor = identity.authenticatePrincipal(token);
            attributes.put("WORK_NO", actor.workNo());
            attributes.put("TENANT_ID", actor.tenantId());
            attributes.put("AUTH_TOKEN", token);
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /** 握手后不记录认证头。
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @param exception 可选握手异常
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) { }
}
