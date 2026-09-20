package com.chandler.fcc.server.websocket.handler;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.websocket.service.ScreenPopDeliveryStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 维护坐席工作台 WebSocket 会话并投递实时话务消息。
 *
 * <p>坐席工号来自通过在线认证的握手属性，浏览器消息不能覆盖投递身份。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private static final String ATTR_WORK_NO = "WORK_NO";
    private static final String ATTR_AUTH_TOKEN = "AUTH_TOKEN";
    private static final String SCREEN_POP_RECEIPT = "SCREEN_POP_RECEIPT";

    private final AgentIdentityService identity;
    private final ScreenPopDeliveryStore deliveries;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> agentSessions =
        new ConcurrentHashMap<>();

    /**
     * 声明业务子协议，认证令牌不会作为协商结果回传。
     *
     * @return 支持的业务子协议
     */
    @Override
    public List<String> getSubProtocols() {
        return List.of("fcc-agent");
    }

    /**
     * 登记已通过握手认证的坐席会话，并补发仍有效的弹屏。
     *
     * @param session WebSocket 会话
     * @throws Exception 消息发送或关闭失败
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String workNo = (String) session.getAttributes().get(ATTR_WORK_NO);
        if (!StringUtils.hasText(workNo)) {
            log.warn("[坐席通道] 建链被拒，缺少认证工号 sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("缺少认证坐席工号"));
            return;
        }

        agentSessions.computeIfAbsent(workNo, ignored -> new CopyOnWriteArraySet<>()).add(session);
        log.info("[坐席通道] 连接成功 sessionId={} workNo={}", session.getId(), workNo);

        WsMessageDTO<String> acknowledgement = WsMessageDTO.of(
            WsMessageTypeEnum.CHANNEL_READY.getCode(),
            workNo,
            null,
            "WebSocket 通道建立成功"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(acknowledgement)));
        if (!authorized(session)) return;

        try {
            for (String pending : deliveries.pending(workNo)) {
                session.sendMessage(new TextMessage(pending));
            }
        } catch (RuntimeException failure) {
            log.warn("[弹屏] 重连补发暂不可用 workNo={}", workNo);
        }
    }

    /**
     * 处理心跳和弹屏回执；话务控制由专用受权接口执行。
     *
     * @param session WebSocket 会话
     * @param message 客户端消息
     * @throws Exception 会话读写失败
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!authorized(session)) return;

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText();
            String workNo = (String) session.getAttributes().get(ATTR_WORK_NO);
            if (WsMessageTypeEnum.HEARTBEAT_PING.getCode().equalsIgnoreCase(type)) {
                WsMessageDTO<String> response = WsMessageDTO.of(
                    WsMessageTypeEnum.HEARTBEAT_PONG.getCode(),
                    workNo,
                    null,
                    "pong"
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } else if (SCREEN_POP_RECEIPT.equals(type)) {
                deliveries.receipt(
                    workNo,
                    root.path("callId").asText(),
                    root.path("state").asText()
                );
            } else if (WsMessageTypeEnum.CALL_CONTROL_ACTION.getCode().equalsIgnoreCase(type)) {
                log.info(
                    "[坐席通道] 收到客户端话务决策 workNo={} action={}",
                    workNo,
                    root.path("action").asText()
                );
            }
        } catch (IOException | IllegalArgumentException failure) {
            log.warn("[坐席通道] 客户端消息无效 sessionId={}", session.getId());
        }
    }

    /**
     * 从在线会话索引中删除已关闭连接。
     *
     * @param session WebSocket 会话
     * @param status 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String workNo = (String) session.getAttributes().get(ATTR_WORK_NO);
        if (StringUtils.hasText(workNo)) {
            CopyOnWriteArraySet<WebSocketSession> sessions = agentSessions.get(workNo);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) agentSessions.remove(workNo, sessions);
            }
        }
        log.info("[坐席通道] 连接关闭 sessionId={} workNo={} status={}", session.getId(), workNo, status);
    }

    /**
     * 关闭发生传输错误的连接。
     *
     * @param session WebSocket 会话
     * @param exception 传输异常
     * @throws Exception 关闭失败
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[坐席通道] 传输异常 sessionId={}", session.getId(), exception);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * 将消息投递给指定坐席的所有有效终端。
     *
     * @param workNo 坐席工号
     * @param message 消息对象
     * @return 成功发送的终端数量
     */
    public int sendToWorkNo(String workNo, Object message) {
        CopyOnWriteArraySet<WebSocketSession> sessions = agentSessions.get(workNo);
        if (sessions == null || sessions.isEmpty()) {
            log.warn("[坐席通道] 坐席当前未在线 workNo={}", workNo);
            return 0;
        }

        int successCount = 0;
        try {
            TextMessage textMessage = new TextMessage(objectMapper.writeValueAsString(message));
            for (WebSocketSession session : sessions) {
                if (session.isOpen() && authorized(session)) {
                    session.sendMessage(textMessage);
                    successCount++;
                }
            }
        } catch (IOException failure) {
            log.error("[坐席通道] 消息投递失败 workNo={}", workNo, failure);
        }
        return successCount;
    }

    /**
     * 向全部已认证在线终端广播消息。
     *
     * @param message 消息对象
     * @return 成功发送的终端数量
     */
    public int broadcast(Object message) {
        int count = 0;
        try {
            TextMessage textMessage = new TextMessage(objectMapper.writeValueAsString(message));
            for (CopyOnWriteArraySet<WebSocketSession> sessions : agentSessions.values()) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen() && authorized(session)) {
                        session.sendMessage(textMessage);
                        count++;
                    }
                }
            }
        } catch (IOException failure) {
            log.error("[坐席通道] 广播失败", failure);
        }
        return count;
    }

    /**
     * 返回在线坐席工号的只读快照视图。
     *
     * @return 在线坐席工号
     */
    public Set<String> getOnlineWorkNos() {
        return Collections.unmodifiableSet(agentSessions.keySet());
    }

    /**
     * 统计当前活动终端连接数。
     *
     * @return 终端连接数
     */
    public int getTotalSessionCount() {
        return agentSessions.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * 在线复核会话令牌与握手工号，防止注销后的长连接继续收发消息。
     *
     * @param session WebSocket 会话
     * @return 身份仍有效时返回 {@code true}
     * @throws IOException 关闭无效会话失败
     */
    private boolean authorized(WebSocketSession session) throws IOException {
        try {
            var actor = identity.authenticatePrincipal(
                (String) session.getAttributes().get(ATTR_AUTH_TOKEN)
            );
            return actor.workNo().equals(session.getAttributes().get(ATTR_WORK_NO));
        } catch (RuntimeException failure) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("登录已失效或认证不可用"));
            return false;
        }
    }
}
