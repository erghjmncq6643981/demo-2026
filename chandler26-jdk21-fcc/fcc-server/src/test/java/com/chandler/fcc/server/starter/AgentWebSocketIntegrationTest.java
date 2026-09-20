package com.chandler.fcc.server.starter;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 坐席工作台 WebSocket 与真实话务弹屏集成测试
 * <p>
 * 覆盖：
 * </p>
 * <ol>
 *   <li>坐席终端 WebSocket 连接握手、工号订阅与心跳治理 (Ping/Pong)；</li>
 *   <li>缺少工号的建链请求被拒绝 (不再回落默认工号)；</li>
 *   <li>真实话务弹屏 (SCREEN_POP) 依据通话事实装配并实时推送，且同一通话对同一坐席只弹一次。</li>
 * </ol>
 *
 * @author Chandler
 * @since 2026-09-19
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext
public class AgentWebSocketIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketIntegrationTest.class);

    private static final String WORK_NO = System.getenv("FCC_TEST_AGENT_WORK_NO");

    @LocalServerPort
    private int port;

    @Autowired
    private ScreenPopService screenPopService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试 1: 验证坐席 WebSocket 连接建立、工号订阅与心跳双向响应")
    void testWebSocketConnectionAndHeartbeat() throws Exception {
        Assumptions.assumeTrue(WORK_NO != null && !WORK_NO.isBlank(), "需配置真实测试坐席工号");
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
        WebSocketSession session = connect(WORK_NO, messageQueue);

        assertNotNull(session, "WebSocket 会话建立失败");
        assertTrue(session.isOpen(), "WebSocket 会话未处于 OPEN 状态");

        // 1. 验证握手成功后服务端下发的通道就绪确认
        String initialMsg = messageQueue.poll(3, TimeUnit.SECONDS);
        assertNotNull(initialMsg, "未收到通道就绪消息");
        JsonNode initNode = objectMapper.readTree(initialMsg);
        assertEquals(WsMessageTypeEnum.CHANNEL_READY.getCode(), initNode.path("type").asText());
        assertEquals(WORK_NO, initNode.path("workNo").asText());
        assertTrue(initNode.path("data").asText().contains(WORK_NO));

        // 2. 发送客户端心跳 Ping
        WsMessageDTO<String> pingMsg = WsMessageDTO.of(
                WsMessageTypeEnum.HEARTBEAT_PING.getCode(),
                WORK_NO,
                null,
                "ping"
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pingMsg)));

        // 3. 验证服务端回复的 Pong
        String pongMsg = messageQueue.poll(3, TimeUnit.SECONDS);
        assertNotNull(pongMsg, "未收到心跳响应");
        JsonNode pongNode = objectMapper.readTree(pongMsg);
        assertEquals(WsMessageTypeEnum.HEARTBEAT_PONG.getCode(), pongNode.path("type").asText());

        session.close();
    }

    @Test
    @DisplayName("测试 2: 缺少认证令牌的握手被拒绝")
    void testConnectionWithoutTokenIsRejected() throws Exception {
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
        assertThrows(ExecutionException.class, () -> connect(null, messageQueue));
        assertNull(messageQueue.poll(500, TimeUnit.MILLISECONDS), "未认证连接不得收到业务消息");
    }

    @Test
    @DisplayName("测试 3: 真实话务弹屏按通话事实装配并推送，同一通话同一坐席只弹一次")
    void testRealScreenPopPush() throws Exception {
        Assumptions.assumeTrue(WORK_NO != null && !WORK_NO.isBlank(), "需配置真实测试坐席工号");
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
        WebSocketSession session = connect(WORK_NO, messageQueue);
        messageQueue.poll(3, TimeUnit.SECONDS);

        String callId = IdUtil.getCallId();
        String callerNumber = "19166340294";
        String didNumber = "9000";

        CallInfoBO call = CallInfoBO.builder()
                .callId(callId)
                .ctrlId(IdUtil.getCtrlId("fcc-live-test"))
                .modelKey(FlowModelType.INBOUND_CUSTOMER_SERVICE.name())
                .direction(DirectionType.INBOUND)
                .callerNumber(callerNumber)
                .destinationNumber(didNumber)
                .data(new HashMap<>())
                .build();

        // 第一次推送：应投递到该坐席
        int delivered = screenPopService.pushForAgentLeg(call, WORK_NO, "1007", 30);
        assertEquals(1, delivered, "应成功向 1 个活跃会话下发弹屏");

        String receivedJson = messageQueue.poll(3, TimeUnit.SECONDS);
        assertNotNull(receivedJson, "客户端未在指定时间内收到弹屏报文");

        JsonNode root = objectMapper.readTree(receivedJson);
        assertEquals(WsMessageTypeEnum.CALL_SCREEN_POP.getCode(), root.path("type").asText());
        assertEquals(WORK_NO, root.path("workNo").asText());
        assertEquals(callId, root.path("callId").asText());

        JsonNode data = root.path("data");
        assertEquals(callId, data.path("callId").asText());
        assertEquals(DirectionType.INBOUND.name(), data.path("direction").asText());
        assertEquals(callerNumber, data.path("callerNumber").asText());
        assertEquals(didNumber, data.path("didNumber").asText());
        assertEquals(30, data.path("ringTimeoutSeconds").asInt());
        assertTrue(data.path("routingReason").asText().contains(WORK_NO),
                "路由依据应体现真实目标坐席: " + data.path("routingReason").asText());

        // 弹屏载荷中不得再出现任何编造的客户档案字段
        assertFalse(data.has("customerLevel"), "不应再下发编造的客户等级");
        assertFalse(data.has("pendingTicketId"), "不应再下发编造的待办工单");

        // 第二次推送同一通话：幂等保护，不应重复弹屏
        int duplicated = screenPopService.pushForAgentLeg(call, WORK_NO, "1007", 30);
        assertEquals(0, duplicated, "同一通话对同一坐席只应弹屏一次");
        assertNull(messageQueue.poll(800, TimeUnit.MILLISECONDS), "不应收到重复弹屏");

        session.close();
    }

    /**
     * 建立一条坐席 WebSocket 连接
     *
     * @param workNo       坐席工号；为 null 时不携带该参数
     * @param messageQueue 服务端消息接收队列
     * @return WebSocket 会话
     */
    private WebSocketSession connect(String workNo, BlockingQueue<String> messageQueue) throws Exception {
        URI uri = new URI("ws://127.0.0.1:" + port + "/ws/agent");
        var headers = new WebSocketHttpHeaders();
        if (workNo != null) {
            String token = System.getenv("FCC_TEST_AGENT_TOKEN");
            Assumptions.assumeTrue(token != null && !token.isBlank(), "需提供本人有效测试令牌");
            headers.setSecWebSocketProtocol(List.of("fcc-agent", "auth." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(token.getBytes(StandardCharsets.UTF_8))));
        }

        StandardWebSocketClient client = new StandardWebSocketClient();
        return client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                log.debug("🧪 [Test Client] 收到服务端消息: {}", message.getPayload());
                messageQueue.offer(message.getPayload());
            }
        }, headers, uri).get(5, TimeUnit.SECONDS);
    }
}
