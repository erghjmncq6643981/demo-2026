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

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentWebSocketHandler, "/ws/agent")
                .setAllowedOrigins("*");
    }
}
