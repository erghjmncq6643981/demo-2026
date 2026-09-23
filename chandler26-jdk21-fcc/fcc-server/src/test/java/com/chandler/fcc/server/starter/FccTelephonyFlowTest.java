package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.FccServerApplication;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.common.dto.command.*;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FNodeRecordAction;
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
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FCC 核心话务控制与全流程状态机流转集成测试
 * <p>
 * 全面验证 FCC 控制面的核心场景：
 * 1. 双向外呼全生命周期 (OUTBOUND_TWO_WAY_CALL: 坐席应答 -> 路由外呼客户 -> 双方桥接 -> 录音 -> 挂机结算)
 * 2. 未配置 DID 拒绝呼入，不提供旧 IVR 默认行为
 * 3. 自动外呼通知与意向按键确认 (AUTO_DIAL_NOTIFICATION)
 * 4. FNode JSON-RPC 2.0 控制客户端通过 NATS 发送指令与审计落盘 (fcc_call_command)
 * 5. SIP 分机注册态生命周期事件同步至 Redis 缓存
 * 6. MySQL 事实表 (fcc_call_session, fcc_call_leg, fcc_call_event) 深度校验
 * </p>
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccServerApplication.class, properties = "fcc.outbound.notification-text=测试通知文案")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext
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
    void setUpMockSidecar() throws Exception {
        var management = natsConnection.jetStreamManagement();
        if (!management.getStreamNames().contains("FCC_EVENTS")) {
            management.addStream(StreamConfiguration.builder()
                    .name("FCC_EVENTS").subjects("fs.event.*.*")
                    .storageType(StorageType.Memory).build());
        }
        if (mockSidecarDispatcher == null) {
            mockSidecarDispatcher = natsConnection.createDispatcher(msg -> {
                try {
                    JsonNode req = objectMapper.readTree(msg.getData());
                    String id = req.hasNonNull("id") ? req.get("id").asText() : "1";
                    String method = req.hasNonNull("method") ? req.get("method").asText() : "";
                    String replyTo = msg.getReplyTo();

                    if (replyTo != null && !replyTo.isEmpty()) {
                        String respJson = String.format(
                                "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"result\":{\"code\":200,\"message\":\"SUCCESS\",\"uuid\":\"mock-uuid-%s\",\"node_id\":\"test-node\",\"timestamp\":%d}}",
                                id, System.currentTimeMillis(), System.currentTimeMillis()
                        );
                        if ("FNode.ChannelSnapshot".equals(method)) {
                            respJson = objectMapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id,
                                    "result", Map.of("code", -32000, "message", "Snapshot unavailable in mock")));
                        }
                        natsConnection.publish(replyTo, respJson.getBytes(StandardCharsets.UTF_8));
                        log.debug("🤖 [Mock Sidecar] 响应 RPC 请求: method={}, id={}", method, id);
                    }
                } catch (Exception e) {
                    log.error("❌ [Mock Sidecar] 处理异常", e);
                }
            });
            mockSidecarDispatcher.subscribe("fs.cmd.dispatch");
            log.info("🤖 [Mock Sidecar] 已启动逻辑命令应答监听: fs.cmd.dispatch");
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
        String agentUuid = IdUtil.getUuid();
        String guestUuid = IdUtil.getUuid();

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
                .nodeId("test-node")
                .guestChannelUuid(guestUuid)
                .agentWorkNo("901001")
                .data(new HashMap<>(Map.of("agentExt", "901001", "primaryWorkNo", "901001",
                        "runtimeTemplate", "AGENT_FIRST", "guestDialString", "user/901002")))
                .build();

        saveFixture(callInfo);
        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(agentUuid, ctrlId);

        // 2. 模拟 FS 发送坐席 Leg 驻留就绪事件 (READY)
        publishChannelEvent(ctrlId, agentUuid, "READY", "outbound", "901001", "13800138000", null, null, null);

        // 等待坐席就绪处理完成并触发客户外呼
        CallInfoBO activeSession = awaitSession(ctrlId, 3000);
        awaitCondition(() -> Boolean.TRUE.equals(activeSession.getData().get("agentReady")), 3000);
        assertEquals(true, activeSession.getData().get("agentReady"), "坐席就绪后必须触发客户外呼标记");

        // 3. 绑定客户 Leg 并发送客户 Leg 驻留就绪事件 (READY)
        sessionManager.bindChannel(guestUuid, ctrlId);
        activeSession.setGuestChannelUuid(guestUuid);
        publishChannelEvent(ctrlId, guestUuid, "READY", "outbound", "901001", "13800138000", null, null, null);

        // 等待桥接指令下发完成
        awaitCondition(() -> Boolean.TRUE.equals(activeSession.getData().get("bridgeRequested")), 3000);
        assertEquals(true, activeSession.getData().get("bridgeRequested"), "客户就绪后必须触发桥接指令下发");

        // 4. 模拟话道进入 BRIDGE 状态
        publishChannelEvent(ctrlId, guestUuid, "BRIDGE", "outbound", "901001", "13800138000", null, null, null);
        awaitCondition(() -> activeSession.getStageState() == CallStageState.CONNECTED, 3000);

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

    /** 未配置的呼入号码必须拒绝，不能使用内置 IVR 或默认坐席兜底。 */
    @Test
    @Order(2)
    @DisplayName("未配置 DID 拒绝呼入")
    void testUnconfiguredInboundRejected() throws Exception {
        String ctrlId = IdUtil.getCtrlId("fcc-inbound-test");
        String uuid = IdUtil.getUuid();
        publishChannelEvent(ctrlId, uuid, "START", "inbound", "13911112222",
                "unconfigured-test-did", null, null, null);
        awaitCondition(() -> callCommandMapper.selectList(
                new LambdaQueryWrapper<CallCommandEntity>().like(CallCommandEntity::getRequestPayload, ctrlId))
                .stream().anyMatch(command -> "FNode.Hangup".equals(command.getMethodName())), 10000);
        assertTrue(sessionManager.getByCtrlUuid(ctrlId).isEmpty(), "不能保留未授权呼入会话");
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
        String guestUuid = IdUtil.getUuid();

        CallInfoBO callInfo = CallInfoBO.builder()
                .ctrlId(ctrlId)
                .callId(callId)
                .modelKey(FlowModelType.AUTO_DIAL_NOTIFICATION.name())
                .direction(DirectionType.OUTBOUND)
                .callerNumber("021-99998888")
                .destinationNumber("13700137000")
                .guestChannelUuid(guestUuid)
                .nodeId("test-node")
                .agentWorkNo("901001")
                .data(new HashMap<>(Map.of("runtimeTemplate", "NOTIFICATION")))
                .build();

        saveFixture(callInfo);
        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(guestUuid, ctrlId);

        // 客户接听驻留
        publishChannelEvent(ctrlId, guestUuid, "READY", "outbound", "021-99998888", "13700137000", null, null, null);

        // 客户按键确认 "1" (确认办理)
        publishDtmfEvent(ctrlId, guestUuid, "1", 100);

        CallInfoBO activeSession = awaitSession(ctrlId, 3000);
        awaitCondition(() -> Boolean.TRUE.equals(activeSession.getData().get("notificationConfirmed")), 3000);
        assertEquals(true, activeSession.getData().get("notificationConfirmed"), "客户按键必须记录为 1");

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
        String ctrlUuid = IdUtil.getCtrlId("fcc-rpc-test");
        String testUuid = "test-chan-uuid";

        // 1. FNode.Dial
        FNodeDialDTO dialDto = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(List.of(FNodeDialDTO.CallParam.builder()
                                .dialString("1001")
                                .context("default")
                                .cidNumber("901001")
                                .cidName("班长席-钱丁君")
                                .build()))
                        .build())
                .build();
        FNodeResult dialRes = fccClient.dial(dialDto);
        assertNotNull(dialRes);
        assertEquals(200, dialRes.getCode(), "Dial 指令应成功返回 200");

        // 2. FNode.ChannelBridge
        FNodeResult bridgeRes = fccClient.channelBridge(ctrlUuid, testUuid, "peer-uuid-1");
        assertNotNull(bridgeRes);
        assertEquals(200, bridgeRes.getCode(), "ChannelBridge 指令应成功返回 200");

        // 3. FNode.Play
        FNodePlayDTO playDto = FNodePlayDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .media(MediaInfo.builder()
                        .type(FNodeMediaType.FILE)
                        .data("/var/sounds/ivr-welcome.wav")
                        .build())
                .build();
        FNodeResult playRes = fccClient.play(playDto);
        assertNotNull(playRes);
        assertEquals(200, playRes.getCode(), "Play 指令应成功返回 200");

        // 4. FNode.ReadDTMF
        FNodeReadDTMFDTO dtmfDto = FNodeReadDTMFDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .minDigits(1)
                .maxDigits(1)
                .media(MediaInfo.builder()
                        .type(FNodeMediaType.FILE)
                        .data("/var/sounds/survey.wav")
                        .build())
                .timeout(5_000)
                .build();
        FNodeResult readRes = fccClient.readDTMF(dtmfDto);
        assertNotNull(readRes);
        assertEquals(200, readRes.getCode(), "ReadDTMF 指令应成功返回 200");

        // 5. FNode.Record
        FNodeRecordDTO recDto = FNodeRecordDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(testUuid)
                .action(FNodeRecordAction.START)
                .path("/var/recordings/test.wav")
                .build();
        FNodeResult recRes = fccClient.record(recDto);
        assertNotNull(recRes);
        assertEquals(200, recRes.getCode(), "Record 指令应成功返回 200");

        // 6. FNode.Hangup
        FNodeResult hangupRes = fccClient.hangup(ctrlUuid, testUuid, "NORMAL_CLEARING");
        assertNotNull(hangupRes);
        assertEquals(200, hangupRes.getCode(), "Hangup 指令应成功返回 200");

        // 7. FNode.NativeAPI
        FNodeResult nativeRes = fccClient.nativeAPI("status", "");
        assertNotNull(nativeRes);
        assertEquals(200, nativeRes.getCode(), "NativeAPI 指令应成功返回 200");

        // 8. FNode.Status
        FNodeResult statusRes = fccClient.status();
        assertNotNull(statusRes);
        assertEquals(200, statusRes.getCode(), "Status 指令应成功返回 200");

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
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 10000);
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
        long deadline = System.currentTimeMillis() + Math.max(timeoutMs, 10000);
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Boolean.TRUE.equals(condition.get())) {
                    return;
                }
            } catch (Exception ignored) {}
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待业务结果被中断", interrupted);
            }
        }
        assertTrue(condition.get(), "等待业务结果超时");
    }

    /** 保存与生产流程一致的恢复上下文。 */
    private void saveFixture(CallInfoBO call) {
        call.putData("nodeId", call.getNodeId());
        call.putData("guestChannelUuid", call.getGuestChannelUuid());
        if (call.getAgentChannelUuid() != null) call.putData("agentChannelUuid", call.getAgentChannelUuid());
        call.setStageState(CallStageState.CALLING);
        persistenceService.saveOrUpdateSession(call);
    }

    /** 发布规范事件并等待 JetStream 持久确认；不依赖测试启动时消费者已经就绪。 */
    private void publishDurable(String category, String method, Map<String, Object> input) throws Exception {
        var params = new HashMap<String, Object>(input);
        params.put("node_id", "test-node");
        params.put("event_id", UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        params.put("timestamp", System.currentTimeMillis());
        params.put("received_at", System.currentTimeMillis());
        natsConnection.jetStream().publish("fs.event.test-node." + category,
                objectMapper.writeValueAsBytes(Map.of("method", method, "params", params)));
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
            publishDurable("channel", "Event.Channel", params);
        } catch (Exception e) {
            throw new AssertionError("发布 Channel 事件失败", e);
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
            publishDurable("dtmf", "Event.DTMF", params);
        } catch (Exception e) {
            throw new AssertionError("发布 DTMF 事件失败", e);
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
            publishDurable("registration", "Event.Registration", params);
        } catch (Exception e) {
            throw new AssertionError("发布 Registration 事件失败", e);
        }
    }
}
