package com.chandler.fcc.server.websocket.config;

import com.chandler.fcc.server.websocket.handler.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 话务服务端 WebSocket 注册装配配置
 * 开放 /ws/agent 终端，支持全域名跨域，保障 Mac 本机与 Parallels Desktop Windows 虚拟机跨网段互通
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentWebSocketHandler agentWebSocketHandler;
    private final AgentHandshakeInterceptor handshake;

    @org.springframework.beans.factory.annotation.Value("${fcc.websocket.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(agentWebSocketHandler, "/ws/agent").addInterceptors(handshake);
        if (!allowedOrigins.isBlank()) {
            String[] origins = java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new);
            if (java.util.Arrays.asList(origins).contains("*")) throw new IllegalArgumentException("WebSocket Origin 不允许通配符");
            registration.setAllowedOrigins(origins);
        }
    }
}
