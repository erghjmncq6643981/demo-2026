package com.chandler.fcc.server.websocket.handler;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 坐席工作台专属 WebSocket 处理器
 * 负责坐席终端会话维护、双向心跳治理与话务控制指令接收
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Slf4j
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 坐席工号 -> WebSocket 会话集合（支持同一坐席多端或多标签页打开）
     */
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> agentSessions = new ConcurrentHashMap<>();

    /**
     * 会话属性键名
     */
    private static final String ATTR_WORK_NO = "WORK_NO";
    private static final String ATTR_AGENT_ID = "AGENT_ID";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String workNo = extractQueryParam(session.getUri(), "workNo");
        String agentId = extractQueryParam(session.getUri(), "agentId");

        // 坐席工号是话务路由与弹屏投递的唯一寻址依据，缺失即拒绝建链，
        // 不再回落默认工号，避免消息被投递到非预期坐席。
        if (!StringUtils.hasText(workNo)) {
            log.warn("⛔ [Agent WebSocket] 建链被拒：缺少 workNo 参数, sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("缺少坐席工号 workNo"));
            return;
        }

        session.getAttributes().put(ATTR_WORK_NO, workNo);
        if (StringUtils.hasText(agentId)) {
            session.getAttributes().put(ATTR_AGENT_ID, agentId);
        }

        agentSessions.computeIfAbsent(workNo, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("🔌 [Agent WebSocket] 坐席连接成功: sessionId={}, workNo={}, 当前在线工号数={}",
                session.getId(), workNo, agentSessions.size());

        // 回传通道就绪确认
        WsMessageDTO<String> ack = WsMessageDTO.of(
                WsMessageTypeEnum.CHANNEL_READY.getCode(),
                workNo,
                null,
                "WebSocket 通道建立成功，已订阅坐席工号: " + workNo
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("📨 [Agent WebSocket] 收到坐席消息: sessionId={}, payload={}", session.getId(), payload);

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            String workNo = (String) session.getAttributes().get(ATTR_WORK_NO);

            if (WsMessageTypeEnum.HEARTBEAT_PING.getCode().equalsIgnoreCase(type)) {
                // 回复心跳
                WsMessageDTO<String> pong = WsMessageDTO.of(
                        WsMessageTypeEnum.HEARTBEAT_PONG.getCode(),
                        workNo,
                        null,
                        "pong"
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
            } else if (WsMessageTypeEnum.CALL_CONTROL_ACTION.getCode().equalsIgnoreCase(type)) {
                log.info("📞 [Agent WebSocket] 收到客户端话务决策: workNo={}, action={}",
                        workNo, root.path("action").asText());
            }
        } catch (Exception e) {
            log.warn("⚠️ [Agent WebSocket] 解析客户端消息异常: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String workNo = (String) session.getAttributes().get(ATTR_WORK_NO);
        if (StringUtils.hasText(workNo)) {
            CopyOnWriteArraySet<WebSocketSession> sessions = agentSessions.get(workNo);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    agentSessions.remove(workNo);
                }
            }
        }
        log.info("❌ [Agent WebSocket] 坐席断开连接: sessionId={}, workNo={}, status={}",
                session.getId(), workNo, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("💥 [Agent WebSocket] 传输异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close();
        }
    }

    /**
     * 发送消息给指定工号的所有会话
     *
     * @param workNo 坐席工号
     * @param message 消息对象
     * @return 成功发送的会话数
     */
    public int sendToWorkNo(String workNo, Object message) {
        CopyOnWriteArraySet<WebSocketSession> sessions = agentSessions.get(workNo);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("⚠️ [Agent WebSocket] 坐席当前未在线，消息无法下发: workNo={}", workNo);
            return 0;
        }

        int successCount = 0;
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                    successCount++;
                }
            }
        } catch (IOException e) {
            log.error("❌ [Agent WebSocket] 序列化或发送消息失败: workNo={}, err={}", workNo, e.getMessage());
        }
        return successCount;
    }

    /**
     * 全员广播消息
     *
     * @param message 消息对象
     * @return 成功广播数
     */
    public int broadcast(Object message) {
        int count = 0;
        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);
            for (CopyOnWriteArraySet<WebSocketSession> sessions : agentSessions.values()) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        count++;
                    }
                }
            }
        } catch (IOException e) {
            log.error("❌ [Agent WebSocket] 广播消息失败: {}", e.getMessage());
        }
        return count;
    }

    /**
     * 获取当前所有在线的坐席工号
     */
    public Set<String> getOnlineWorkNos() {
        return Collections.unmodifiableSet(agentSessions.keySet());
    }

    /**
     * 获取当前活动的 WebSocket 连接总数
     */
    public int getTotalSessionCount() {
        return agentSessions.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * 从 URI 中提取查询参数
     */
    private String extractQueryParam(URI uri, String paramName) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String[] pairs = uri.getQuery().split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length >= 2 && paramName.equalsIgnoreCase(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
