package com.chandler.fcc.server.websocket.config;

import com.chandler.fcc.server.websocket.handler.AgentWebSocketHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 话务服务端 WebSocket 注册装配配置
 * 开放 /ws/agent 终端，支持配置的源及本地常用端口跨域
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

    @Value("${fcc.websocket.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(agentWebSocketHandler, "/ws/agent").addInterceptors(handshake);
        
        List<String> origins = new ArrayList<>();
        // 默认放行本地前端常用开发端口与局域网
        origins.add("http://localhost:8888");
        origins.add("http://127.0.0.1:8888");
        origins.add("http://localhost:8000");
        origins.add("http://127.0.0.1:8000");
        origins.add("http://192.168.3.132:8888");
        origins.add("http://192.168.3.132:8000");

        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty() && !"*".equals(origin) && !"\"*\"".equals(origin))
                .forEach(origins::add);
        }
        
        registration.setAllowedOrigins(origins.toArray(String[]::new));
    }
}
