package com.chandler.fcc.server.event;

import com.chandler.fcc.common.dto.event.EventRegistrationDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.protocol.FccEventMethods;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.action.DefaultActionExecutorsManager;
import com.chandler.fcc.server.flow.event.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * FCC 核心事件监听网关
 * <p>
 * 订阅 NATS 消息总线（主题通配符: fs.event.>），接收 Go Sidecar Agent 对 FreeSWITCH 底层 ESL
 * 进行清洗、标准化后的 Event.Channel（通道状态机）、Event.DTMF（按键收号）、Event.Registration（SIP注册态）
 * 及 Event.Recording（录音落盘）事件，驱动控制面状态机流转并发布 Spring 领域事件。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FccEventListener {

    private final Connection natsConnection;
    private final ApplicationEventPublisher publisher;
    private final CallSessionManager sessionManager;
    private final DefaultActionExecutorsManager handlersManager;
    private final FccClient fccClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService callPersistenceService;

    @Autowired(required = false)
    private com.chandler.fcc.server.websocket.service.AgentWebSocketService agentWebSocketService;

    @Autowired(required = false)
    private com.chandler.fcc.server.recording.RecordingPathResolver recordingPathResolver;

    private static final String EXTENSION_PRESENCE_PREFIX = "fcc:extension:presence:";

    @Autowired(required=false)
    private com.chandler.fcc.server.agent.application.PhoneBindingService phoneBindingService;
    @Autowired(required=false)
    private com.chandler.fcc.server.telephony.application.OutboundCallService outboundCallService;
    @Autowired(required=false)
    private com.chandler.fcc.server.telephony.application.InboundCallService inboundCallService;
    @Autowired(required=false)
    private EventInboxMapper inbox;
    @Autowired(required=false)
    private com.chandler.fcc.server.call.CallRecoveryService recovery;
    private final java.util.concurrent.ExecutorService consumer = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    /**
     * 服务启动后初始化 NATS 分发器并订阅事件通道
     */
    @PostConstruct
    public void startListening() {
        try {
            consumer.submit(() -> {
                while (running) {
                    try {
                        if(recovery!=null)recovery.restore();
                        var options=io.nats.client.PullSubscribeOptions.builder().stream("FCC_EVENTS").durable("fcc-control")
                            .configuration(io.nats.client.api.ConsumerConfiguration.builder().ackPolicy(io.nats.client.api.AckPolicy.Explicit)
                                .ackWait(Duration.ofSeconds(90)).maxAckPending(1).maxDeliver(-1).build()).build();
                        var subscription=natsConnection.jetStream().subscribe("fs.event.>",options);
                        while(running){
                            for(Message message:subscription.fetch(1,Duration.ofSeconds(2))){
                                try{processDurable(message);message.ack();}
                                catch(Exception failure){message.nakWithDelay(Duration.ofSeconds(5));log.warn("[事件消费] 等待重试: {}",failure.getClass().getSimpleName());}
                            }
                        }
                    } catch(Exception failure){
                        log.warn("[事件消费] 等待 FCC_EVENTS 流可用: {}",failure.getClass().getSimpleName());
                        try{Thread.sleep(5000);}catch(InterruptedException stop){Thread.currentThread().interrupt();return;}
                    }
                }
            });
        } catch (Exception e) {
            log.error("❌ [FCC] 订阅 NATS 事件异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 接收并分发 NATS 原始事件消息
     *
     * @param msg NATS 消息报文
     */
    public void onMessage(Message msg) {
        try {
            String jsonStr = new String(msg.getData(), StandardCharsets.UTF_8);
            log.debug("[FCC 收到事件] Subject: {}", msg.getSubject());

            JsonNode root = objectMapper.readTree(jsonStr);
            String method = root.hasNonNull("method") ? root.get("method").asText() : "";
            JsonNode params = root.get("params");
            if (params == null || params.isNull()) {
                return;
            }

            if (FccEventMethods.CHANNEL.equalsIgnoreCase(method)) {
                handleChannelEvent(params);
            } else if (FccEventMethods.DTMF.equalsIgnoreCase(method)) {
                handleDTMFEvent(params);
            } else if (FccEventMethods.REGISTRATION.equalsIgnoreCase(method)) {
                handleRegistrationEvent(params);
            } else if (FccEventMethods.RECORDING.equalsIgnoreCase(method)) {
                handleRecordingEvent(params);
            }
        } catch (Exception e) {
            log.error("❌ [FCC] 处理 NATS 事件异常: {}", e.getMessage(), e);
            throw new IllegalStateException("事件处理未完成",e);
        }
    }

    /** 验证消息归属与稳定身份，先持久事实，再驱动业务。
     * @param message 持久消息
     * @throws Exception 校验或存储不可用
     */
    private void processDurable(Message message) throws Exception {
        JsonNode params=objectMapper.readTree(message.getData()).path("params");
        String id=params.path("event_id").asText(),node=params.path("node_id").asText();
        if(!id.matches("[a-f0-9]{64}")||node.isBlank()||!message.getSubject().startsWith("fs.event."+node+".")) {
            log.error("[事件消费] 拒绝非法身份消息 subject={}",message.getSubject());message.term();return;
        }
        if(inbox==null)throw new IllegalStateException("事件收件箱不可用");
        if(inbox.receive(id,node,new String(message.getData(),StandardCharsets.UTF_8))==0){
            if("PROCESSING".equals(inbox.status(id))){inbox.finish(id,"UNKNOWN");log.error("[事件消费] 中断事件需要对账 eventId={}",id);}
            return;
        }
        try{onMessage(message);inbox.finish(id,"PROCESSED");}
        catch(Exception failure){inbox.finish(id,"FAILED");log.error("[事件消费] 事件失败已留存 eventId={}",id);}
    }

    /** 停止单线程有界拉取消费。 */
    @jakarta.annotation.PreDestroy
    public void stopListening(){running=false;consumer.shutdownNow();}

    /**
     * 处理通道状态机流转事件 (Event.Channel)
     *
     * @param params 事件载荷
     */
    private void handleChannelEvent(JsonNode params) {
        String nodeId = getNodeText(params, "node_id");
        if (nodeId == null || nodeId.isBlank()) {
            log.warn("[话务事件] 缺少节点归属，拒绝处理 Channel 事件");
            return;
        }
        String state = getNodeText(params, "state");
        String uuid = getNodeText(params, "uuid");
        String peerUuid = getNodeText(params, "peer_uuid");
        String ctrlUuid = getNodeText(params, "ctrl_uuid");
        String direction = getNodeText(params, "direction");
        String cidNumber = getNodeText(params, "cid_number");
        String destNumber = getNodeText(params, "dest_number");
        Integer duration = params.hasNonNull("duration") ? params.get("duration").asInt() : null;
        Integer billsec = params.hasNonNull("billsec") ? params.get("billsec").asInt() : null;
        String cause = getNodeText(params, "cause");

        CallInfoBO callInfo = sessionManager.getByCtrlUuid(ctrlUuid)
                .or(() -> sessionManager.getByChannelUuid(uuid))
                .orElse(null);

        if (callInfo == null) {
            if (!"START".equals(state) || !"inbound".equalsIgnoreCase(direction)) {
                log.warn("[话务事件] 未关联的话道事件待对账 nodeId={} channelUuid={} state={}", nodeId,uuid,state);
                return;
            }
            boolean isInbound = "inbound".equalsIgnoreCase(direction);
            String effectiveCtrlUuid = (ctrlUuid != null && !ctrlUuid.isEmpty())
                    ? ctrlUuid
                    : IdUtil.getCtrlId("fcc-inbound");
            String effectiveCallId = IdUtil.getCallId();
            String modelKey = isInbound ? FlowModelType.INBOUND_CUSTOMER_SERVICE.name() : FlowModelType.OUTBOUND_TWO_WAY_CALL.name();

            callInfo = CallInfoBO.builder()
                    .nodeId(nodeId)
                    .callId(effectiveCallId)
                    .ctrlId(effectiveCtrlUuid)
                    .guestChannelUuid(uuid)
                    .agentChannelUuid(peerUuid)
                    .modelKey(modelKey)
                    .direction(isInbound ? DirectionType.INBOUND : DirectionType.OUTBOUND)
                    .callerNumber(cidNumber)
                    .destinationNumber(destNumber)
                    .duration(duration)
                    .billsec(billsec)
                    .hangupCause(cause)
                    .data(new HashMap<>())
                    .build();
            callInfo.putData("ctrlId", effectiveCtrlUuid);
            callInfo.putData("callId", effectiveCallId);
            callInfo.putData("guestChannelUuid", uuid);
            callInfo.putData("enableSurvey", "true");

            sessionManager.registerSession(callInfo);
            sessionManager.bindChannel(uuid, effectiveCtrlUuid);
        } else {
            if (callInfo.getNodeId() != null && !nodeId.equals(callInfo.getNodeId())) {
                log.warn("[话务事件] 节点归属不一致，拒绝改变会话: callId={}", callInfo.getCallId());
                return;
            }
            callInfo.setNodeId(nodeId);
            if (duration != null) callInfo.setDuration(duration);
            if (billsec != null) callInfo.setBillsec(billsec);
            if (cause != null) callInfo.setHangupCause(cause);
            if (peerUuid != null && callInfo.getAgentChannelUuid() == null) {
                callInfo.setAgentChannelUuid(peerUuid);
            }
        }

        callInfo.putData("flowEventId",params.path("event_id").asText());
        callInfo.putData("flowSourceTime",params.path("timestamp").asLong());
        if (phoneBindingService != null && phoneBindingService.channel(callInfo, params)) {
            if ("DESTROY".equals(state)) sessionManager.removeSession(callInfo.getCtrlId());
            return;
        }
        if (outboundCallService != null && outboundCallService.event(callInfo,params)) return;
        if (inboundCallService != null && inboundCallService.event(callInfo,params)) return;
        log.info("📞 [FCC 状态机流转] State: {}, UUID: {}, CtrlID: {}, Model: {}, Caller: {}, Dest: {}",
                state, uuid, callInfo.getCtrlId(), callInfo.getModelKey(), cidNumber, destNumber);

        switch (state != null ? state.toUpperCase() : "") {
            case "START":
                if (callInfo.getDirection() == DirectionType.INBOUND && callInfo.getData().putIfAbsent("started", "true") == null) {
                    callInfo.setStageState(CallStageState.START);
                    publisher.publishEvent(new CallStartEvent(callInfo));
                }
                break;

            case "CALLING":
            case "RINGING":
                callInfo.setStageState(CallStageState.CALLING);
                publisher.publishEvent(new CallCallingEvent(callInfo));
                break;

            case "READY":
                handleChannelReady(callInfo, uuid);
                break;

            case "BRIDGE":
                if (callInfo.getData().putIfAbsent("connected", "true") == null) {
                    callInfo.setStageState(CallStageState.CONNECTED);
                    publisher.publishEvent(new CallConnectedEvent(callInfo));
                    if (agentWebSocketService != null) {
                        String targetWorkNo = resolveAgentWorkNo(callInfo);
                        if (targetWorkNo != null) {
                            agentWebSocketService.pushCallAnswered(targetWorkNo, callInfo.getCallId(), java.util.Map.of("callId", callInfo.getCallId()));
                        }
                    }
                }
                break;

            case "DESTROY":
                handleChannelDestroy(callInfo, uuid, ctrlUuid, params);
                if (agentWebSocketService != null && callInfo.getData().putIfAbsent("ws_hangup_pushed", "true") == null) {
                    String targetWorkNo = resolveAgentWorkNo(callInfo);
                    if (targetWorkNo != null) {
                        agentWebSocketService.pushCallHangup(targetWorkNo, callInfo.getCallId(), java.util.Map.of("callId", callInfo.getCallId(), "cause", cause != null ? cause : "NORMAL_CLEARING"));
                    }
                }
                break;

            default:
                log.debug("ℹ️ [FCC] 状态暂无需特殊处理: {}", state);
                break;
        }

        if (callPersistenceService != null) {
            try {
                // 1. 同步落盘通话会话
                callPersistenceService.saveOrUpdateSession(callInfo);

                // 2. 同步落盘话道 Leg
                boolean isAgentLeg = uuid.equals(callInfo.getAgentChannelUuid());
                com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity leg =
                        com.chandler.fcc.server.infrastructure.persistence.entity.CallLegEntity.builder()
                                .channelUuid(uuid)
                                .callId(com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService.parseNumericId(callInfo.getCallId()))
                                .nodeId(nodeId)
                                .roleType(isAgentLeg ? "AGENT" : "CUSTOMER")
                                .direction(direction != null ? direction.toUpperCase() : (callInfo.getDirection() != null ? callInfo.getDirection().name() : "INBOUND"))
                                .callerNumber(cidNumber != null ? cidNumber : callInfo.getCallerNumber())
                                .destinationNumber(destNumber != null ? destNumber : callInfo.getDestinationNumber())
                                .state(state)
                                .hangupCause(cause)
                                .build();
                if ("ANSWERED".equalsIgnoreCase(state) || "READY".equalsIgnoreCase(state)) {
                    leg.setAnsweredAt(java.time.LocalDateTime.now());
                } else if ("BRIDGE".equalsIgnoreCase(state)) {
                    leg.setBridgedAt(java.time.LocalDateTime.now());
                } else if ("DESTROY".equalsIgnoreCase(state)) {
                    leg.setEndedAt(java.time.LocalDateTime.now());
                }
                callPersistenceService.saveOrUpdateLeg(leg);

                // 3. 记录事件流
                callPersistenceService.recordEvent(com.chandler.fcc.server.infrastructure.persistence.entity.CallEventEntity.builder()
                        .eventId(params.path("event_id").asText(IdUtil.getEventId()))
                        .nodeId(nodeId)
                        .callId(com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService.parseNumericId(callInfo.getCallId()))
                        .channelUuid(uuid)
                        .eventType("Event.Channel." + state)
                        .rawPayload(params.toString())
                        .eventTime(java.time.LocalDateTime.now())
                        .processStatus("PROCESSED")
                        .build());
            } catch (Exception dbEx) {
                log.warn("⚠️ 话务持久化审计记录异常: {}", dbEx.getMessage());
            }
        }
    }

    /**
     * 处理话道 Park 静默驻留就绪逻辑
     */
    private void handleChannelReady(CallInfoBO callInfo, String uuid) {
        // 呼叫转接应答协同
        String transferTargetUuid = callInfo.getDataStr("transferTargetAgentUuid", null);
        if (transferTargetUuid != null && transferTargetUuid.equals(uuid)) {
            callInfo.getData().remove("transferTargetAgentUuid");
            callInfo.getData().remove("isTransferring");
            callInfo.setAgentChannelUuid(uuid);
            callInfo.putData("agentChannelUuid", uuid);

            log.info("🔗 [呼叫转接协同] 目标坐席 {} 已应答就绪，重新桥接客户话道 {} 与目标坐席！",
                    uuid, callInfo.getGuestChannelUuid());

            FlowNode bridgeNode = FlowNode.builder()
                    .actionType(ActionType.CHANNEL_BRIDGE)
                    .actionKey("bridge-transfer-target")
                    .order(1)
                    .data(new HashMap<>(Map.of(
                            "uuidA", callInfo.getGuestChannelUuid(),
                            "uuidB", uuid,
                            "ctrlUuid", callInfo.getCtrlId() != null ? callInfo.getCtrlId() : ""
                    )))
                    .build();
            handlersManager.publish(callInfo, bridgeNode);
            return;
        }

        String modelKey = callInfo.getModelKey() != null ? callInfo.getModelKey() : FlowModelType.INBOUND_CUSTOMER_SERVICE.name();

        if (FlowModelType.OUTBOUND_TWO_WAY_CALL.name().equals(modelKey)) {
            // 双向外呼：坐席 Leg 就绪 -> 外呼客户 Leg
            if (uuid.equals(callInfo.getAgentChannelUuid())) {
                if (callInfo.getData().putIfAbsent("guestDialed", "true") == null) {
                    callInfo.setStageState(CallStageState.ROUTE);
                    log.info("🎯 [双向外呼协同] 坐席已应答驻留，开始路由外呼客户: {}", callInfo.getDestinationNumber());
                    publisher.publishEvent(new CallRouteEvent(callInfo));
                }
            }
            // 客户 Leg 也应答驻留 -> 双方桥接
            else if (uuid.equals(callInfo.getGuestChannelUuid()) || "true".equals(callInfo.getData().get("guestDialed"))) {
                String agentChan = callInfo.getAgentChannelUuid();
                String guestChan = callInfo.getGuestChannelUuid();
                if (agentChan != null && guestChan != null) {
                    if (callInfo.getData().putIfAbsent("bridgeDispatched", "true") == null) {
                        log.info("🔗 [双向外呼协同] 双方均已应答 (READY)，自动触发话道桥接! Agent: {}, Guest: {}",
                                agentChan, guestChan);
                        Map<String, Object> bridgeData = new HashMap<>();
                        bridgeData.put("guestChannelUuid", guestChan);
                        bridgeData.put("agentChannelUuid", agentChan);
                        bridgeData.put("ctrlId", callInfo.getCtrlId() != null ? callInfo.getCtrlId() : "");

                        FlowNode bridgeNode = FlowNode.builder()
                                .actionType(ActionType.CHANNEL_BRIDGE)
                                .actionKey("bridge-agent-guest")
                                .order(1)
                                .data(bridgeData)
                                .build();
                        handlersManager.publish(callInfo, bridgeNode);
                    }
                }
            }
        } else if (FlowModelType.AUTO_DIAL_NOTIFICATION.name().equals(modelKey)) {
            // 自动外呼通知：客户接听就绪 -> 播放通知语音并收号
            if (uuid.equals(callInfo.getGuestChannelUuid())) {
                if (callInfo.getData().putIfAbsent("notifyStarted", "true") == null) {
                    callInfo.setStageState(CallStageState.ROUTE);
                    log.info("📢 [自动通知协同] 客户已接听话道就绪，触发通知放音收号: Guest={}", uuid);
                    publisher.publishEvent(new CallRouteEvent(callInfo));
                }
            }
        } else {
            // 呼入客服模式
            if (uuid.equals(callInfo.getGuestChannelUuid())) {
                if (callInfo.getData().putIfAbsent("started", "true") == null) {
                    callInfo.setStageState(CallStageState.START);
                    log.info("🎯 [呼入流程协同] 客户通道已就绪，启动 IVR 导航放音收号: Guest={}", uuid);
                    publisher.publishEvent(new CallStartEvent(callInfo));
                }
            } else if (uuid.equals(callInfo.getAgentChannelUuid()) || "true".equals(callInfo.getData().get("agentDialed"))) {
                String agentChan = callInfo.getAgentChannelUuid();
                String guestChan = callInfo.getGuestChannelUuid();
                if (agentChan != null && guestChan != null) {
                    if (callInfo.getData().putIfAbsent("bridgeDispatched", "true") == null) {
                        log.info("🔗 [呼入流程协同] 客户与坐席均已就绪，触发话道桥接! Guest: {}, Agent: {}",
                                guestChan, agentChan);
                        Map<String, Object> bridgeData = new HashMap<>();
                        bridgeData.put("guestChannelUuid", guestChan);
                        bridgeData.put("agentChannelUuid", agentChan);
                        bridgeData.put("ctrlId", callInfo.getCtrlId() != null ? callInfo.getCtrlId() : "");

                        FlowNode bridgeNode = FlowNode.builder()
                                .actionType(ActionType.CHANNEL_BRIDGE)
                                .actionKey("bridge-inbound-agent")
                                .order(1)
                                .data(bridgeData)
                                .build();
                        handlersManager.publish(callInfo, bridgeNode);
                    }
                }
            }
        }
    }

    /**
     * 处理通道销毁挂断逻辑
     */
    private void handleChannelDestroy(CallInfoBO callInfo, String uuid, String ctrlUuid, JsonNode params) {
        String hungupUuid = uuid;

        // 呼叫转接保护
        if ("true".equals(callInfo.getData().get("isTransferring")) && hungupUuid.equals(callInfo.getData().get("originalAgentUuid"))) {
            log.info("🔀 [呼叫转接协同] 原坐席话道 {} 已挂断退出，客户话道驻留等待目标坐席应答", hungupUuid);
            return;
        }

        if (uuid.equals(callInfo.getAgentChannelUuid())) callInfo.putData("agentEnded", "true");
        if (uuid.equals(callInfo.getGuestChannelUuid())) callInfo.putData("guestEnded", "true");

        // 检查通道变量中的收号评分
        if (params.has("params")) {
            JsonNode extra = params.get("params");
            String dtmfVal = extra.hasNonNull("dtmf_val") ? extra.get("dtmf_val").asText() : null;
            if (dtmfVal != null && !dtmfVal.isEmpty() && !"_none_".equalsIgnoreCase(dtmfVal) && callInfo.getData().get("surveyScore") == null) {
                callInfo.putData("surveyScore", dtmfVal);
                try {
                    callInfo.setEvaluationScore(Integer.parseInt(dtmfVal));
                } catch (NumberFormatException ignored) {}
                handlersManager.recordAudit(callInfo, "survey-score-recorded", "READ_DTMF", Map.of(
                        "score", dtmfVal,
                        "digit", dtmfVal,
                        "result", "SUCCESS",
                        "detail", "用户按键评价成功: " + dtmfVal + "分",
                        "targetUuid", uuid
                ));
                log.info("⭐ [满意度评价完成] 话道结算捕获用户按键评分: {} 分, CallID: {}", dtmfVal, callInfo.getCallId());
            }
        }

        String survivingUuid = null;
        if ("true".equals(callInfo.getData().get("agentEnded")) && !"true".equals(callInfo.getData().get("guestEnded"))) {
            survivingUuid = callInfo.getGuestChannelUuid();
        } else if ("true".equals(callInfo.getData().get("guestEnded")) && !"true".equals(callInfo.getData().get("agentEnded"))) {
            survivingUuid = callInfo.getAgentChannelUuid();
        } else if (hungupUuid.equals(callInfo.getGuestChannelUuid())) {
            survivingUuid = callInfo.getAgentChannelUuid();
        } else if (hungupUuid.equals(callInfo.getAgentChannelUuid())) {
            survivingUuid = callInfo.getGuestChannelUuid();
        }

        callInfo.putData("hungupUuid", hungupUuid);
        if (survivingUuid != null) {
            callInfo.putData("survivingUuid", survivingUuid);
            callInfo.putData("peerUuid", survivingUuid);
        }

        // 首次通道销毁触发业务挂断事件
        if (callInfo.getData().putIfAbsent("callEndFired", "true") == null) {
            callInfo.setStageState(CallStageState.NORMAL_END);
            log.info("🏁 [FCC] 话道挂机触发 CallEndEvent: Hungup={}, Surviving={}", hungupUuid, survivingUuid);
            publisher.publishEvent(new CallEndEvent(callInfo));
        }

        // 双方均已销毁时释放内存会话
        if ("true".equals(callInfo.getData().get("agentEnded")) && "true".equals(callInfo.getData().get("guestEnded"))) {
            if ("true".equalsIgnoreCase(callInfo.getDataStr("enableSurvey", "false")) && callInfo.getData().get("surveyScore") == null) {
                handlersManager.recordAudit(callInfo, "survey-timeout", "READ_DTMF", Map.of(
                        "result", "TIMEOUT",
                        "detail", "用户未按键，引导语重复播放2次后超时自动挂机",
                        "callUuid", callInfo.getCallId() != null ? callInfo.getCallId() : ""
                ));
                log.info("⌛ [满意度评价结果] 用户未按键，引导语重复播放超时挂机: CallID={}", callInfo.getCallId());
            }
            if (ctrlUuid != null) {
                sessionManager.removeSession(ctrlUuid);
            }
        }
    }

    /**
     * 处理按键收号事件 (Event.DTMF)
     *
     * @param params 按键载荷
     */
    private void handleDTMFEvent(JsonNode params) {
        String ctrlUuid = getNodeText(params, "ctrl_uuid");
        String uuid = getNodeText(params, "uuid");
        String digit = getNodeText(params, "digit");
        int durationMs = params.hasNonNull("duration_ms") ? params.get("duration_ms").asInt() : 0;

        if (digit == null || digit.isEmpty() || "_none_".equalsIgnoreCase(digit)) {
            return;
        }

        CallInfoBO bindingCall=sessionManager.getByCtrlUuid(ctrlUuid).or(()->sessionManager.getByChannelUuid(uuid)).orElse(null);
        if(bindingCall!=null){bindingCall.putData("flowEventId",params.path("event_id").asText());bindingCall.putData("flowSourceTime",params.path("timestamp").asLong());}
        if(bindingCall!=null && inboundCallService!=null && inboundCallService.digits(bindingCall,params))return;
        if (bindingCall!=null && phoneBindingService!=null && phoneBindingService.digits(bindingCall,digit)) return;
        if (bindingCall!=null && outboundCallService!=null && outboundCallService.digits(bindingCall,digit)) return;
        log.debug("[FCC 收到按键] UUID: {}, CtrlUUID: {}", uuid, ctrlUuid);
        publisher.publishEvent(new DTMFInputEvent(this, ctrlUuid, uuid, digit, durationMs));

        sessionManager.getByCtrlUuid(ctrlUuid)
                .or(() -> sessionManager.getByChannelUuid(uuid))
                .ifPresent(session -> {
                    String modelKey = session.getModelKey();

                    // 场景 A: 呼入 IVR 导航按键选择
                    if (FlowModelType.INBOUND_CUSTOMER_SERVICE.name().equals(modelKey) && session.getData().get("agentDialed") == null) {
                        session.putData("agentDialed", "true");
                        session.putData("ivrSelectedDigit", digit);

                        // 动态根据坐席接听方式解析目标分机
                        String targetExt = null;
                        String targetWorkNo = null;
                        String targetAgentName = null;
                        if (jdbcTemplate != null) {
                            try {
                                java.util.List<java.util.Map<String, Object>> agents = jdbcTemplate.queryForList(
                                        "SELECT a.work_no, a.agent_name, a.current_extension, " +
                                        "b.endpoint_type, b.endpoint_value " +
                                        "FROM fcc_agent a " +
                                        "LEFT JOIN fcc_agent_endpoint_binding b ON a.id = b.agent_id AND b.status = 'ENABLED' " +
                                        "WHERE a.status = 'ENABLED' AND a.deleted_at IS NULL " +
                                        "AND (a.current_extension IS NOT NULL OR b.endpoint_value IS NOT NULL) " +
                                        "ORDER BY b.priority ASC, a.updated_at DESC LIMIT 1");
                                if (!agents.isEmpty()) {
                                    java.util.Map<String, Object> a = agents.getFirst();
                                    targetWorkNo = String.valueOf(a.get("work_no"));
                                    Object agentNameVal = a.get("agent_name");
                                    targetAgentName = agentNameVal != null ? String.valueOf(agentNameVal) : null;
                                    String curExt = (String) a.get("current_extension");
                                    String epType = (String) a.get("endpoint_type");
                                    String epVal = (String) a.get("endpoint_value");

                                    if ("SIP".equalsIgnoreCase(epType) && curExt != null && !curExt.isBlank()) {
                                        targetExt = curExt;
                                    } else if ("SIP".equalsIgnoreCase(epType) && epVal != null && !epVal.isBlank()) {
                                        targetExt = epVal;
                                    } else if ("WEBRTC".equalsIgnoreCase(epType)) {
                                        targetExt = targetWorkNo;
                                    } else if (curExt != null && !curExt.isBlank()) {
                                        targetExt = curExt;
                                    } else {
                                        targetExt = targetWorkNo;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("⚠️ 动态查询坐席接听分机异常: {}", e.getMessage());
                            }
                        }

                        if (targetWorkNo == null || targetExt == null) {
                            log.warn("⚠️ [呼入导航] 没有可用坐席终端，停止路由: digit={}, ctrlId={}", digit, session.getCtrlId());
                            handlersManager.recordAudit(session, "ivr-navigation-no-agent", "READ_DTMF", Map.of(
                                    "digit", digit,
                                    "result", "FAILED",
                                    "detail", "没有可用坐席终端",
                                    "targetUuid", uuid
                            ));
                            return;
                        }

                        session.setAgentExt(targetExt);
                        session.setAgentWorkNo(targetWorkNo);
                        session.putData("agentExt", targetExt);
                        session.putData("primaryWorkNo", targetWorkNo);
                        if (targetAgentName != null && !targetAgentName.isBlank()) {
                            session.putData("agentName", targetAgentName);
                        }
                        log.info("🎯 [呼入导航] 客户按键选择: {} 业务，路由坐席工号 {} -> 目标分机/终端 {}", digit, targetWorkNo, targetExt);
                        handlersManager.recordAudit(session, "ivr-navigation-selected", "READ_DTMF", Map.of(
                                "digit", digit,
                                "result", "SUCCESS",
                                "detail", "客户按键选择业务: " + digit + " (路由工号" + targetWorkNo + "至终端" + targetExt + ")",
                                "targetUuid", uuid
                        ));

                        session.setStageState(CallStageState.ROUTE);
                        publisher.publishEvent(new CallRouteEvent(session));
                        return;
                    }

                    // 场景 B: 自动外呼通知意向按键确认
                    if (FlowModelType.AUTO_DIAL_NOTIFICATION.name().equals(modelKey)) {
                        if (session.getData().putIfAbsent("notifyDigit", digit) == null) {
                            String intentDesc = "1".equals(digit) ? "确认办理" : ("2".equals(digit) ? "咨询详情" : "其他业务");
                            handlersManager.recordAudit(session, "notification-confirmed", "READ_DTMF", Map.of(
                                    "digit", digit,
                                    "result", "SUCCESS",
                                    "detail", "客户按键确认意向: " + digit + " (" + intentDesc + ")",
                                    "durationMs", String.valueOf(durationMs),
                                    "targetUuid", uuid
                            ));
                            log.info("📢 [自动通知按键确认] 客户按键确认意向: {} ({}), Channel={}", digit, intentDesc, uuid);
                        }
                        return;
                    }

                    // 场景 C: 满意度评价按键
                    boolean canSurvey = "true".equals(session.getData().get("callEndFired"))
                            || "true".equals(session.getData().get("agentEnded"))
                            || session.getStageState() == CallStageState.NORMAL_END;
                    if (canSurvey && session.getData().putIfAbsent("surveyScore", digit) == null) {
                        try {
                            session.setEvaluationScore(Integer.parseInt(digit));
                        } catch (NumberFormatException ignored) {}
                        handlersManager.recordAudit(session, "survey-score-recorded", "READ_DTMF", Map.of(
                                "score", digit,
                                "digit", digit,
                                "result", "SUCCESS",
                                "detail", "用户实时按键评价: " + digit + "分",
                                "durationMs", String.valueOf(durationMs),
                                "targetUuid", uuid
                        ));
                        log.info("⭐ [满意度评价完成] 实时捕获用户按键评分: {} 分, Channel={}", digit, uuid);
                    }
                });
    }

    /**
     * 处理 SIP 分机注册态生命周期事件 (Event.Registration)
     *
     * @param params 注册态参数
     */
    private void handleRegistrationEvent(JsonNode params) {
        String nodeId = getNodeText(params, "node_id");
        String user = getNodeText(params, "user");
        String domain = getNodeText(params, "domain");
        String status = getNodeText(params, "status");
        String networkIp = getNodeText(params, "network_ip");
        Integer port = params.hasNonNull("port") ? params.get("port").asInt() : 5060;
        String userAgent = getNodeText(params, "user_agent");
        String contact = getNodeText(params, "contact");
        long timestamp = params.hasNonNull("timestamp") ? params.get("timestamp").asLong() : System.currentTimeMillis();

        EventRegistrationDTO regDTO = EventRegistrationDTO.builder()
                .nodeId(nodeId)
                .user(user)
                .domain(domain)
                .status(status)
                .networkIp(networkIp)
                .port(port)
                .userAgent(userAgent)
                .contact(contact)
                .timestamp(timestamp)
                .build();

        log.info("📱 [SIP 分机注册态变更] Node: {}, Ext: {}, Status: {}, IP: {}, UA: {}",
                nodeId, user, status, networkIp, userAgent);

        // 同步写入 Redis 分机在线态（租约 1 小时）
        if (stringRedisTemplate != null && user != null) {
            try {
                String key = EXTENSION_PRESENCE_PREFIX + user;
                if ("REGISTERED".equalsIgnoreCase(status)) {
                    stringRedisTemplate.opsForValue().set(key, "ONLINE", Duration.ofHours(1));
                } else {
                    stringRedisTemplate.opsForValue().set(key, "OFFLINE", Duration.ofHours(1));
                }
            } catch (Exception e) {
                log.warn("⚠️ [Redis] 同步分机注册态缓存失败: {}", e.getMessage());
            }
        }

        // 广播 Spring 领域事件
        publisher.publishEvent(new ExtensionRegistrationEvent(this, regDTO));
    }

    /**
     * 处理录音事件（{@code Event.Recording}）
     * <p>
     * 录音地址的事实来源是「下发指令时声明的共享路径」，本方法只做补齐与结账：
     * START 事件到来时将地址登记为 RECORDING，STOP 事件到来时补齐时长、文件大小并结账为 COMPLETED。
     * 两次事件共用同一个 {@code recording_id}，以幂等合并方式落库。
     * </p>
     *
     * @param params 录音事件参数
     */
    private void handleRecordingEvent(JsonNode params) {
        String nodeId = getNodeText(params, "node_id");
        String ctrlUuid = getNodeText(params, "ctrl_uuid");
        String channelUuid = getNodeText(params, "uuid");
        String action = getNodeText(params, "action");
        String declaredPath = getNodeText(params, "file_path");
        Integer seconds = getNodeInt(params, "seconds");
        boolean stopping = "stop".equalsIgnoreCase(action);

        log.info("🎙️ [录音事件] Node: {}, Ctrl: {}, Channel: {}, Action: {}, Path: {}, Seconds: {}",
                nodeId, ctrlUuid, channelUuid, action, declaredPath, seconds);

        if (callPersistenceService == null) {
            return;
        }

        // 1. 先由控制关联标识回推业务通话标识，录音文件名与 recording_id 均以业务通话为基准
        String businessCallId = sessionManager.getByCtrlUuid(ctrlUuid)
                .map(CallInfoBO::getCallId)
                .orElse(null);
        String recordingKey = firstNonBlank(businessCallId, channelUuid);
        if (recordingKey == null) {
            log.warn("⚠️ [录音事件] 无法定位录音归属，事件已忽略: ctrlUuid={}, channelUuid={}", ctrlUuid, channelUuid);
            return;
        }

        // 2. 事件未携带地址时，按共享存储布局补算，保证库内始终有可读地址
        String recordPath = declaredPath;
        if ((recordPath == null || recordPath.isBlank()) && recordingPathResolver != null) {
            try {
                recordPath = recordingPathResolver.resolve(recordingKey, java.time.LocalDateTime.now(), null).absolutePath();
            } catch (Exception e) {
                log.warn("⚠️ [录音事件] 补算录音地址失败: key={}, err={}", recordingKey, e.getMessage());
            }
        }

        try {
            Long numericCallId = businessCallId != null
                    ? com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService.parseNumericId(businessCallId)
                    : IdUtil.nextId();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            callPersistenceService.upsertRecording(
                    com.chandler.fcc.server.infrastructure.persistence.entity.CallRecordingEntity.builder()
                            .recordingId(com.chandler.fcc.common.recording.RecordingPathLayout.recordingIdOf(recordingKey))
                            .callId(numericCallId)
                            .nodeId(nodeId)
                            .status(stopping ? "COMPLETED" : "RECORDING")
                            .storageType("LOCAL")
                            .objectKey(recordPath)
                            .mediaFormat(mediaFormatOf(recordPath))
                            .durationMs(seconds != null ? seconds * 1000L : null)
                            .sizeBytes(stopping ? fileSizeOf(recordPath) : null)
                            .startedAt(now)
                            .completedAt(stopping ? now : null)
                            .build());
        } catch (Exception recEx) {
            log.warn("⚠️ 记录录音元数据失败: {}", recEx.getMessage());
        }
    }

    /**
     * 读取共享存储上录音文件的字节大小
     * <p>
     * 控制面与 FreeSWITCH 挂载同一路径，因此可以直接取到真实文件尺寸；
     * 文件尚未落盘或不可读时返回 null，不影响元数据其他字段的写入。
     * </p>
     *
     * @param recordPath 录音文件共享绝对路径
     * @return 文件字节数；不可获取时返回 null
     */
    private Long fileSizeOf(String recordPath) {
        if (recordPath == null || recordPath.isBlank()) {
            return null;
        }
        try {
            java.nio.file.Path file = java.nio.file.Paths.get(recordPath.trim());
            if (java.nio.file.Files.isRegularFile(file)) {
                long size = java.nio.file.Files.size(file);
                return size > 0 ? size : null;
            }
        } catch (Exception e) {
            log.debug("读取录音文件大小失败: path={}, err={}", recordPath, e.getMessage());
        }
        return null;
    }

    /**
     * 从录音路径推断封装格式
     *
     * @param recordPath 录音文件路径
     * @return 小写扩展名，缺省为 wav
     */
    private String mediaFormatOf(String recordPath) {
        if (recordPath == null) {
            return "wav";
        }
        int dot = recordPath.lastIndexOf('.');
        return (dot < 0 || dot == recordPath.length() - 1) ? "wav" : recordPath.substring(dot + 1).toLowerCase();
    }

    private String getNodeText(JsonNode node, String fieldName) {
        if (node != null && node.hasNonNull(fieldName)) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    /**
     * 读取整型事件字段
     *
     * @param node      JSON 节点
     * @param fieldName 字段名
     * @return 字段值；缺失或非数字时返回 null
     */
    private Integer getNodeInt(JsonNode node, String fieldName) {
        if (node != null && node.hasNonNull(fieldName)) {
            JsonNode value = node.get(fieldName);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 返回第一个非空白取值
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 解析通话当前实际的接待坐席工号
     * <p>
     * 只返回<b>确实由业务流转确定</b>的工号：坐席路由完成后才会有值，
     * 未确定时返回 null，调用方据此跳过推送，避免把通知误投到默认工号。
     * </p>
     *
     * @param callInfo 通话上下文
     * @return 坐席工号；无法确定时返回 null
     */
    private String resolveAgentWorkNo(CallInfoBO callInfo) {
        if (callInfo == null) {
            return null;
        }
        return firstNonBlank(callInfo.getAgentWorkNo(), callInfo.getDataStr("primaryWorkNo", null));
    }
}
