package com.chandler.fcc.server.starter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.common.dto.command.*;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallCommandEntity;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity;
import com.chandler.fcc.server.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallCommandMapper;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallLegMapper;
import com.chandler.fcc.server.infrastructure.persistence.mapper.CallSessionMapper;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FCC 核心话务控制与全流程状态机流转集成测试
 * <p>
 * 全面验证 FCC 控制面的核心场景：
 * 1. 双向外呼全生命周期 (OUTBOUND_TWO_WAY_CALL: 坐席应答 -> 路由外呼客户 -> 双方桥接 -> 录音 -> 挂机结算)
 * 2. 呼入客服 IVR 导航按键与满意度按键评价流转 (INBOUND_CUSTOMER_SERVICE)
 * 3. 自动外呼通知与意向按键确认 (AUTO_DIAL_NOTIFICATION)
 * 4. FNode JSON-RPC 2.0 控制客户端通过 NATS 发送指令与审计落盘 (fcc_call_command)
 * 5. SIP 分机注册态生命周期事件同步至 Redis 缓存
 * 6. MySQL 事实表 (fcc_call_session, fcc_call_leg, fcc_call_event) 深度校验
 * </p>
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccServerApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FccTelephonyFlowTest {

    private static final Logger log = LoggerFactory.getLogger(FccTelephonyFlowTest.class);

    @Autowired
    private Connection natsConnection;

    @Autowired
    private FccClient fccClient;

    @Autowired
    private CallSessionManager sessionManager;

    @Autowired
    private CallPersistenceService persistenceService;

    @Autowired
    private CallSessionMapper callSessionMapper;

    @Autowired
    private CallLegMapper callLegMapper;

    @Autowired
    private CallCommandMapper callCommandMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Dispatcher mockSidecarDispatcher;

    /**
     * 测试初始化：启动 Mock Go-Sidecar Agent 的 NATS JSON-RPC 指令应答分发器
     */
    @BeforeEach
    void setUpMockSidecar() {
        if (mockSidecarDispatcher == null) {
            mockSidecarDispatcher = natsConnection.createDispatcher(msg -> {
                try {
                    JsonNode req = objectMapper.readTree(msg.getData());
                    String id = req.hasNonNull("id") ? req.get("id").asText() : "1";
                    String method = req.hasNonNull("method") ? req.get("method").asText() : "";
                    String replyTo = msg.getReplyTo();

                    if (replyTo != null && !replyTo.isEmpty()) {
                        String respJson = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{\"code\":0,\"message\":\"SUCCESS\",\"uuid\":\"mock-uuid-%s\",\"nodeId\":\"test-node\",\"timestamp\":%d}}",
                                id, System.currentTimeMillis(), System.currentTimeMillis()
                        );
                        natsConnection.publish(replyTo, respJson.getBytes(StandardCharsets.UTF_8));
                        log.debug("🤖 [Mock Sidecar] 响应 RPC 请求: method={}, id={}", method, id);
                    }
                } catch (Exception e) {
                    log.error("❌ [Mock Sidecar] 处理异常", e);
                }
            });
            mockSidecarDispatcher.subscribe("fs.cmd.test-node");
            log.info("🤖 [Mock Sidecar] 已启动 NATS 指令应答监听: fs.cmd.test-node");
        }
    }

    /**
     * 测试用例 1: 验证双向外呼完整生命周期流转与 MySQL 事实表持久化
     * <p>
     * 场景：班长席钱丁君 (工号 901001) 发起外呼客户 13800138000：
     * 1. 注册会话 -> 坐席 Leg 就绪 (READY) -> 驱动路由外呼客户 Leg (ROUTE)
     * 2. 客户 Leg 就绪 (READY) -> 驱动媒体桥接 (BRIDGE)
     * 3. 双方通话 -> 坐席先挂机 (DESTROY) -> 触发结算 (NORMAL_END)
     * 4. 校验 fcc_call_session 及 fcc_call_leg 记录
     * </p>
     */
    @Test
    @Order(1)
    @DisplayName("测试双向外呼生命周期流转与数据库持久化")
    void testOutboundTwoWayCallLifecycle() throws Exception {
        String ctrlId = IdUtil.getCtrlId("fcc-outbound-test");
        String callId = IdUtil.getCallId();
        String agentUuid = "agent-leg-" + System.currentTimeMillis();
        String guestUuid = "guest-leg-" + System.currentTimeMillis();

        // 1. 初始化会话 (与 TelephonyCallController.outbound 真实建会话方式一致：
        //    工号落在 agentWorkNo 与 primaryWorkNo 上，agentExt 只表达终端分机)
        CallInfoBO callInfo = CallInfoBO.builder()
                .ctrlId(ctrlId)
                .callId(callId)
                .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
                .direction(DirectionType.OUTBOUND)
                .callerNumber("901001") // 班长席 钱丁君
                .destinationNumber("13800138000")
                .agentChannelUuid(agentUuid)
                .agentWorkNo("901001")
                .agentExt("901001")
                .data(new HashMap<>(Map.of("agentExt", "901001", "primaryWorkNo", "901001", "operator", "钱丁君")))
                .build();

        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(agentUuid, ctrlId);

        // 2. 模拟 FS 发送坐席 Leg 驻留就绪事件 (READY)
        publishChannelEvent(ctrlId, agentUuid, "READY", "outbound", "901001", "13800138000", null, null, null);

        // 等待坐席就绪处理完成并触发客户外呼
        CallInfoBO activeSession = awaitSession(ctrlId, 3000);
        awaitCondition(() -> "true".equals(activeSession.getData().get("guestDialed")), 3000);
        assertEquals("true", activeSession.getData().get("guestDialed"), "坐席就绪后必须触发客户外呼标记");

        // 3. 绑定客户 Leg 并发送客户 Leg 驻留就绪事件 (READY)
        sessionManager.bindChannel(guestUuid, ctrlId);
        activeSession.setGuestChannelUuid(guestUuid);
        publishChannelEvent(ctrlId, guestUuid, "READY", "outbound", "901001", "13800138000", null, null, null);

        // 等待桥接指令下发完成
        awaitCondition(() -> "true".equals(activeSession.getData().get("bridgeDispatched")), 3000);
        assertEquals("true", activeSession.getData().get("bridgeDispatched"), "客户就绪后必须触发桥接指令下发");

        // 4. 模拟话道进入 BRIDGE 状态
        publishChannelEvent(ctrlId, guestUuid, "BRIDGE", "outbound", "901001", "13800138000", null, null, null);
        awaitCondition(() -> "true".equals(activeSession.getData().get("connected")), 3000);

        // 5. 模拟挂机拆线 (DESTROY)，时长 60 秒，计费 55 秒
        publishChannelEvent(ctrlId, agentUuid, "DESTROY", "outbound", "901001", "13800138000", 60, 55, "NORMAL_CLEARING");
        publishChannelEvent(ctrlId, guestUuid, "DESTROY", "outbound", "901001", "13800138000", 60, 55, "NORMAL_CLEARING");

        // 6. 验证 MySQL 数据库持久化
        Long numericCallId = CallPersistenceService.parseNumericId(callId);
        awaitCondition(() -> {
            CallSessionEntity e = callSessionMapper.selectById(numericCallId);
            return e != null && "NORMAL_END".equals(e.getStatus());
        }, 4000);

        CallSessionEntity dbSession = callSessionMapper.selectById(numericCallId);
        assertNotNull(dbSession, "fcc_call_session 表必须存在该通话记录");
        assertEquals("NORMAL_END", dbSession.getStatus(), "最终状态必须为 NORMAL_END");
        assertEquals("901001", dbSession.getPrimaryWorkNo(), "主服务坐席工号必须为 901001 (钱丁君)");
        assertEquals("901001", dbSession.getAgentWorkNo(), "接待坐席工号必须落库，作为后续防撞单的事实来源");
        assertEquals("NORMAL_CLEARING", dbSession.getResult(), "挂机结果码应为 NORMAL_CLEARING");
        assertEquals(60000L, dbSession.getTotalDurationMs(), "总时长必须计算正确 (60000ms)");
        assertEquals(55000L, dbSession.getTalkDurationMs(), "通话时长必须计算正确 (55000ms)");

        // 验证 fcc_call_leg
        List<CallLegEntity> dbLegs = callLegMapper.selectList(
                new LambdaQueryWrapper<CallLegEntity>().eq(CallLegEntity::getCallId, numericCallId)
        );
        assertFalse(dbLegs.isEmpty(), "fcc_call_leg 必须记录话道");
        log.info("✅ [测试通过] 双向外呼生命周期流转与数据库事实记录校验成功！CallId: {}", callId);
    }

    /**
     * 测试用例 2: 验证呼入客服 IVR 导航按键与满意度按键评价流转
     * <p>
     * 场景：外部客户 13911112222 呼入服务号 9000：
     * 1. 产生呼入事件 (START) -> 自动创建会话并启动 IVR
     * 2. 客户按键 "1" 选择业务 -> 自动路由客服坐席 1007
     * 3. 坐席接听桥接 -> 坐席挂断 -> 客户按键 "5" 进行满意度评价
     * 4. 校验评价分数字段 evaluation_score 成功落盘为 5
     * </p>
     */
    @Test
    @Order(2)
    @DisplayName("测试呼入客服 IVR 导航与满意度评价流程")
    void testInboundCustomerServiceWithIvrAndSatisfactionSurvey() throws Exception {
        String inboundCtrlId = IdUtil.getCtrlId("fcc-inbound-test");
        String guestUuid = "guest-inbound-" + System.currentTimeMillis();

        // 1. 模拟客户呼入 9000 (START)
        publishChannelEvent(inboundCtrlId, guestUuid, "START", "inbound", "13911112222", "9000", null, null, null);

        CallInfoBO session = awaitSession(inboundCtrlId, 3000);
        assertEquals(FlowModelType.INBOUND_CUSTOMER_SERVICE.name(), session.getModelKey(), "呼入业务流模式匹配");

        // 2. 模拟客户 IVR 按键 "1"
        publishDtmfEvent(inboundCtrlId, guestUuid, "1", 120);
        awaitCondition(() -> "1".equals(session.getData().get("ivrSelectedDigit")), 3000);

        // 断言已选择业务 1 并触发路由
        assertEquals("1", session.getData().get("ivrSelectedDigit"), "客户按键必须记录为 1");
        assertEquals("true", session.getData().get("agentDialed"), "必须触发坐席外呼");

        // 3. 模拟坐席 1007 挂断，客户未挂断并按键 "5" 进行满意度评价
        String agentUuid = "agent-ext-1007";
        session.setAgentChannelUuid(agentUuid);
        sessionManager.bindChannel(agentUuid, inboundCtrlId);

        publishChannelEvent(inboundCtrlId, agentUuid, "DESTROY", "inbound", "13911112222", "9000", 30, 25, "NORMAL_CLEARING");
        awaitCondition(() -> "true".equals(session.getData().get("agentEnded")), 3000);

        // 模拟满意度收号按键 "5"
        publishDtmfEvent(inboundCtrlId, guestUuid, "5", 150);
        awaitCondition(() -> Integer.valueOf(5).equals(session.getEvaluationScore()), 3000);

        // 客户挂机
        publishChannelEvent(inboundCtrlId, guestUuid, "DESTROY", "inbound", "13911112222", "9000", 35, 25, "NORMAL_CLEARING");

        // 4. 验证数据库满意度评价落盘
        Long numericCallId = CallPersistenceService.parseNumericId(session.getCallId());
        awaitCondition(() -> {
            CallSessionEntity e = callSessionMapper.selectById(numericCallId);
            return e != null && e.getEvaluationScore() != null && e.getEvaluationScore() == 5;
        }, 4000);

        CallSessionEntity dbSession = callSessionMapper.selectById(numericCallId);
        assertNotNull(dbSession, "会话必须成功落库");
        assertEquals(5, dbSession.getEvaluationScore(), "服务满意度评分必须为 5 分");
        log.info("✅ [测试通过] 呼入客服 IVR 导航与 5 星满意度评价流程校验成功！Score: 5");
    }

    /**
     * 测试用例 3: 验证自动外呼通知流程与意向按键确认
     * <p>
     * 场景：自动外呼客户 13700137000 进行通知播报，客户接听后按 "1" 确认办理业务
     * </p>
     */
    @Test
    @Order(3)
    @DisplayName("测试自动外呼通知与意向按键确认")
    void testAutoDialNotificationWithDtmfConfirmation() throws Exception {
        String ctrlId = IdUtil.getCtrlId("fcc-autodial-test");
        String callId = IdUtil.getCallId();
        String guestUuid = "guest-notify-" + System.currentTimeMillis();

        CallInfoBO callInfo = CallInfoBO.builder()
                .ctrlId(ctrlId)
                .callId(callId)
                .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name())
                .direction(DirectionType.OUTBOUND)
                .callerNumber("021-99998888")
                .destinationNumber("13700137000")
                .guestChannelUuid(guestUuid)
                .data(new HashMap<>())
                .build();

        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(guestUuid, ctrlId);

        // 客户接听驻留
        publishChannelEvent(ctrlId, guestUuid, "READY", "outbound", "021-99998888", "13700137000", null, null, null);

        // 客户按键确认 "1" (确认办理)
        publishDtmfEvent(ctrlId, guestUuid, "1", 100);

        CallInfoBO activeSession = awaitSession(ctrlId, 3000);
        awaitCondition(() -> "1".equals(activeSession.getData().get("notifyDigit")), 3000);
        assertEquals("1", activeSession.getData().get("notifyDigit"), "客户按键必须记录为 1");

        // 结束呼叫
        publishChannelEvent(ctrlId, guestUuid, "DESTROY", "outbound", "021-99998888", "13700137000", 20, 15, "NORMAL_CLEARING");
        TimeUnit.MILLISECONDS.sleep(200);

        log.info("✅ [测试通过] 自动外呼通知与意向按键确认流转成功！");
    }

    /**
     * 测试用例 4: 验证 FccClient 全部 FNode JSON-RPC 2.0 控制指令及审计落盘
     * <p>
     * 覆盖: Dial, ChannelBridge, Play, ReadDTMF, Record, Hangup, NativeAPI, Status
     * </p>
     */
    @Test
    @Order(4)
    @DisplayName("测试 FNode JSON-RPC 2.0 控制客户端指令发送与审计记录")
    void testFccClientRpcCommandsAndAudit() {
        String testNodeId = "test-node";
        String ctrlUuid = IdUtil.getCtrlId("fcc-rpc-test");
        String testUuid = "test-chan-uuid";

        // 1. FNode.Dial
        FNodeDialDTO dialDto = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(List.of(FNodeDialDTO.CallParam.builder()
                                .dialString("user/1001")
                                .cidNumber("901001")
                                .cidName("班长席-钱丁君")
                                .build()))
                        .build())
                .build();
        FNodeResult dialRes = fccClient.dial(testNodeId, dialDto);
        assertNotNull(dialRes);
        assertEquals(0, dialRes.getCode(), "Dial 指令应成功返回 0");

        // 2. FNode.ChannelBridge
        FNodeResult bridgeRes = fccClient.channelBridge(testNodeId, ctrlUuid, testUuid, "peer-uuid-1");
        assertNotNull(bridgeRes);
        assertEquals(0, bridgeRes.getCode(), "ChannelBridge 指令应成功返回 0");

        // 3. FNode.Play
        FNodePlayDTO playDto = FNodePlayDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .media(MediaInfo.builder()
                        .type("FILE")
                        .data("/var/sounds/ivr-welcome.wav")
                        .build())
                .build();
        FNodeResult playRes = fccClient.play(testNodeId, playDto);
        assertNotNull(playRes);
        assertEquals(0, playRes.getCode(), "Play 指令应成功返回 0");

        // 4. FNode.ReadDTMF
        FNodeReadDTMFDTO dtmfDto = FNodeReadDTMFDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .minDigits(1)
                .maxDigits(1)
                .media(MediaInfo.builder()
                        .type("FILE")
                        .data("/var/sounds/survey.wav")
                        .build())
                .timeout(5)
                .build();
        FNodeResult readRes = fccClient.readDTMF(testNodeId, dtmfDto);
        assertNotNull(readRes);
        assertEquals(0, readRes.getCode(), "ReadDTMF 指令应成功返回 0");

        // 5. FNode.Record
        FNodeRecordDTO recDto = FNodeRecordDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .action("START")
                .path("/var/recordings/test.wav")
                .build();
        FNodeResult recRes = fccClient.record(testNodeId, recDto);
        assertNotNull(recRes);
        assertEquals(0, recRes.getCode(), "Record 指令应成功返回 0");

        // 6. FNode.Hangup
        FNodeResult hangupRes = fccClient.hangup(testNodeId, ctrlUuid, testUuid, "NORMAL_CLEARING");
        assertNotNull(hangupRes);
        assertEquals(0, hangupRes.getCode(), "Hangup 指令应成功返回 0");

        // 7. FNode.NativeAPI
        FNodeResult nativeRes = fccClient.nativeAPI(testNodeId, "status", "");
        assertNotNull(nativeRes);
        assertEquals(0, nativeRes.getCode(), "NativeAPI 指令应成功返回 0");

        // 8. FNode.Status
        FNodeResult statusRes = fccClient.status(testNodeId);
        assertNotNull(statusRes);
        assertEquals(0, statusRes.getCode(), "Status 指令应成功返回 0");

        // 校验 fcc_call_command 表至少记录了上述指令审计
        List<CallCommandEntity> commands = callCommandMapper.selectList(
                new LambdaQueryWrapper<CallCommandEntity>()
                        .orderByDesc(CallCommandEntity::getCreatedAt)
                        .last("LIMIT 8")
        );
        assertFalse(commands.isEmpty(), "fcc_call_command 表必须审计记录已发送指令");
        assertTrue(commands.stream().anyMatch(c -> "FNode.Dial".equals(c.getMethodName())), "必须审计 Dial 指令");
        log.info("✅ [测试通过] FNode JSON-RPC 2.0 8 项控制指令发送与数据库审计落盘校验成功！");
    }

    /**
     * 测试用例 5: 验证 SIP 分机注册态生命周期事件同步至 Redis 缓存
     * <p>
     * 场景：分机 1001 注册上线 (REGISTERED) -> 离线注销 (UNREGISTERED)
     * </p>
     */
    @Test
    @Order(5)
    @DisplayName("测试 SIP 分机注册态变更与 Redis 在线态同步")
    void testSipExtensionRegistrationAndPresenceSync() throws Exception {
        if (stringRedisTemplate == null) {
            log.warn("⚠️ Redis 未连接，跳过 Redis 断言");
            return;
        }

        String extension = "1001";
        String presenceKey = "fcc:extension:presence:" + extension;

        // 1. 模拟分机注册上线
        publishRegistrationEvent(extension, "REGISTERED", "192.168.1.105", 5060, "Linphone/5.1.0");
        awaitCondition(() -> "ONLINE".equals(stringRedisTemplate.opsForValue().get(presenceKey)), 3000);

        String presence = stringRedisTemplate.opsForValue().get(presenceKey);
        assertEquals("ONLINE", presence, "注册事件发生后 Redis 在线状态必须为 ONLINE");

        // 2. 模拟分机注销下线
        publishRegistrationEvent(extension, "UNREGISTERED", "192.168.1.105", 5060, "Linphone/5.1.0");
        awaitCondition(() -> "OFFLINE".equals(stringRedisTemplate.opsForValue().get(presenceKey)), 3000);

        presence = stringRedisTemplate.opsForValue().get(presenceKey);
        assertEquals("OFFLINE", presence, "注销事件发生后 Redis 在线状态必须为 OFFLINE");

        log.info("✅ [测试通过] SIP 分机注册态变更与 Redis 缓存同步校验成功！");
    }

    // ==================== 工具辅助方法 ====================

    private CallInfoBO awaitSession(String ctrlId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var opt = sessionManager.getByCtrlUuid(ctrlId);
            if (opt.isPresent()) {
                return opt.get();
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException ignored) {}
        }
        return sessionManager.getByCtrlUuid(ctrlId).orElseThrow();
    }

    private void awaitCondition(Supplier<Boolean> condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(condition.get())) {
                    return;
                }
            } catch (Exception ignored) {}
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException ignored) {}
        }
    }

    private void publishChannelEvent(String ctrlUuid, String uuid, String state, String direction,
                                     String cidNumber, String destNumber, Integer duration, Integer billsec, String cause) {
        Map<String, Object> params = new HashMap<>();
        params.put("ctrl_uuid", ctrlUuid);
        params.put("uuid", uuid);
        params.put("state", state);
        params.put("direction", direction);
        params.put("cid_number", cidNumber);
        params.put("dest_number", destNumber);
        if (duration != null) params.put("duration", duration);
        if (billsec != null) params.put("billsec", billsec);
        if (cause != null) params.put("cause", cause);

        Map<String, Object> event = Map.of(
                "method", "Event.Channel",
                "params", params
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(event);
            natsConnection.publish("fs.event.channel", bytes);
        } catch (Exception e) {
            log.error("发布 Channel 事件失败", e);
        }
    }

    private void publishDtmfEvent(String ctrlUuid, String uuid, String digit, int durationMs) {
        Map<String, Object> params = Map.of(
                "ctrl_uuid", ctrlUuid != null ? ctrlUuid : "",
                "uuid", uuid,
                "digit", digit,
                "duration_ms", durationMs
        );
        Map<String, Object> event = Map.of(
                "method", "Event.DTMF",
                "params", params
        );
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(event);
            natsConnection.publish("fs.event.dtmf", bytes);
        } catch (Exception e) {
            log.error("发布 DTMF 事件失败", e);
        }
    }

    private void publishRegistrationEvent(String user, String status, String networkIp, int port, String userAgent) {
        Map<String, Object> params = Map.of(
                "node_id", "test-node",
                "user", user,
                "domain", "127.0.0.1",
                "status", status,
                "network_ip", networkIp,
                "port", port,
                "user_agent", userAgent,
                "timestamp", System.currentTimeMillis()
        );
        Map<String, Object> event = Map.of(
                "method", "Event.Registration",
                "params", params
        );
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(event);
            natsConnection.publish("fs.event.registration", bytes);
        } catch (Exception e) {
            log.error("发布 Registration 事件失败", e);
        }
    }
}
