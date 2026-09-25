package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 咨询转接服务 (带双方回铃音与 30s 超时自动回退桥接)
 *
 * <p>核心流程：
 * 1. 坐席 A 发起向分机 C 的转接；
 * 2. 临时关闭 A 与 B 的 hangup_after_bridge，并将 A 与 B 解除桥接转入 park；
 * 3. 对 A 和 B 同时广播放音播放标准回铃音；
 * 4. 向分机 C 发起呼叫，超时时间 30s；
 * 5. 若 C 在 30s 内接听：停止 A、B 放音，桥接 B 与 C，挂断 A（A 释放入后处理）；
 * 6. 若 C 超时 30s 未接听 / 拒接 / 忙线：停止 A、B 放音，拆除 C，重新桥接 A 与 B，并通知坐席恢复通话。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallTransferService {

    private static final String RINGBACK_TONE = "tone_stream://%(2000,4000,440,480);loops=-1";
    private static final int TRANSFER_TIMEOUT_SECONDS = 30;

    private final CallSessionManager sessions;
    private final FccClient client;
    private final CallPersistenceService persistence;
    private final AgentWebSocketService websocket;
    private final AgentRuntimeMapper agents;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fcc-transfer-watchdog");
        t.setDaemon(true);
        return t;
    });

    /**
     * 发起咨询转接
     *
     * @param call 当前通话
     * @param target 转接目标号码/分机
     * @return 受理结果
     */
    public Map<String, Object> initiateTransfer(CallInfoBO call, String target) {
        if (target == null || !target.matches("[+0-9]{1,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效转接号码");
        }
        if (call.getStageState() != CallStageState.CONNECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前通话未处于通话中，无法转接");
        }

        synchronized (call) {
            if (Boolean.TRUE.equals(call.getData().get("transferPending"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前通话正在转接中，请勿重复发起");
            }

            String uuidA = call.getAgentChannelUuid();
            String uuidB = call.getGuestChannelUuid();
            if (uuidA == null || uuidB == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "有效话道尚未确认");
            }

            String uuidC = IdUtil.getUuid();
            call.putData("transferPending", true);
            call.putData("transferSuccess", false);
            call.putData("transferTarget", target);
            call.putData("transferChannelUuid", uuidC);
            call.putData("transferAgentUuid", uuidA);
            call.putData("transferGuestUuid", uuidB);
            call.putData("transferStartTime", System.currentTimeMillis());

            sessions.bindChannel(uuidC, call.getCtrlId());
            persistence.saveOrUpdateSession(call);

            log.info("🔀 [发起转接] callId={}, A(坐席)={}, B(客户)={}, 目标C={}, uuidC={}",
                call.getCallId(), uuidA, uuidB, target, uuidC);

            try {
                // 1. 确保 A 和 B 均已应答 (防止 loopback / originate 出现假超时)
                try {
                    client.nativeAPI("uuid_answer", uuidA);
                    client.nativeAPI("uuid_answer", uuidB);
                } catch (Exception ignored) {}

                // 2. 临时关闭 A 和 B 的 hangup_after_bridge，防止 unbridge 时被自动挂断
                client.nativeAPI("uuid_setvar", uuidA + " hangup_after_bridge false");
                client.nativeAPI("uuid_setvar", uuidB + " hangup_after_bridge false");
                client.nativeAPI("uuid_setvar", uuidA + " park_after_bridge true");
                client.nativeAPI("uuid_setvar", uuidB + " park_after_bridge true");

                // 3. 将 A 和 B 解除桥接并转入 park
                client.nativeAPI("uuid_transfer", uuidA + " -both park inline");
                client.nativeAPI("uuid_transfer", uuidB + " park inline");

                // 4. 对处于 park 状态的 A 和 B 广播放音，播放循环标准回铃音
                client.nativeAPI("uuid_broadcast", uuidA + " " + RINGBACK_TONE + " aleg");
                client.nativeAPI("uuid_broadcast", uuidB + " " + RINGBACK_TONE + " aleg");

                // 5. 向分机 C 发起呼叫 (带上 30 秒超时)
                FNodeDialDTO dialDto = FNodeDialDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(uuidC)
                    .timeout(TRANSFER_TIMEOUT_SECONDS)
                    .destination(
                        FNodeDialDTO.Destination.builder()
                            .callParams(
                                List.of(
                                    FNodeDialDTO.CallParam.builder()
                                        .uuid(uuidC)
                                        .dialString(target)
                                        .context("default")
                                        .cidNumber(call.getCallerNumber() != null ? call.getCallerNumber() : "FCC")
                                        .cidName("FCC-TRANSFER")
                                        .build()
                                )
                            )
                            .build()
                    )
                    .build();
                client.dial(dialDto);

                // 6. 启动 31 秒安全超时兜底定时器
                scheduler.schedule(() -> handleTransferTimeout(call.getCallId()), 31, TimeUnit.SECONDS);

            } catch (Exception e) {
                log.error("❌ [发起转接失败] 恢复 A 与 B 桥接 callId={}", call.getCallId(), e);
                client.nativeAPI("uuid_break", uuidA + " all");
                client.nativeAPI("uuid_break", uuidB + " all");
                client.nativeAPI("uuid_setvar", uuidA + " hangup_after_bridge true");
                client.nativeAPI("uuid_setvar", uuidB + " hangup_after_bridge true");
                client.channelBridge(call.getCtrlId(), uuidA, uuidB);
                call.getData().remove("transferPending");
                call.getData().remove("transferChannelUuid");
                persistence.saveOrUpdateSession(call);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "发起转接失败: " + e.getMessage());
            }

            return Map.of(
                "code", 200,
                "message", "转接指令已受理，正在呼叫分机 " + target,
                "data", Map.of("callId", call.getCallId(), "status", "ACCEPTED")
            );
        }
    }

    /**
     * 处理通道生命周期事件，拦截转接相关话道。
     *
     * @param call 通话业务对象
     * @param params 事件参数
     * @param state 事件状态
     * @param channelUuid 发生事件的话道 UUID
     * @return 是否由转接服务消费
     */
    public boolean handleChannelEvent(CallInfoBO call, JsonNode params, ChannelEventState state, String channelUuid) {
        String uuidC = (String) call.getData().get("transferChannelUuid");

        // 1. 事件属于转接目标话道 C
        if (uuidC != null && uuidC.equals(channelUuid)) {
            boolean answered = state == ChannelEventState.ANSWERED
                || (params != null && params.path("answered").asBoolean(false) && state != ChannelEventState.DESTROY);
            if (answered) {
                handleTransferAnswer(call, uuidC);
                return true;
            }
            if (state == ChannelEventState.DESTROY) {
                String cause = params != null ? params.path(FccEventField.CAUSE.getWireName()).asText("NO_ANSWER") : "NO_ANSWER";
                handleTransferFailure(call, cause);
                return true;
            }
            // START, CALLING, RINGING, MEDIA, READY 等状态均视为转接呼叫中，目标尚未应答，无需其它服务干预
            return true;
        }

        // 2. 转接中话道 A 的事件
        String uuidA = (String) call.getData().get("transferAgentUuid");
        if (uuidA != null && uuidA.equals(channelUuid)) {
            if (Boolean.TRUE.equals(call.getData().get("transferPending"))) {
                if (state == ChannelEventState.UNBRIDGE || state == ChannelEventState.READY) {
                    return true; // 忽略 park / unbridge 事件
                }
                if (state == ChannelEventState.DESTROY) {
                    String cause = params != null ? params.path(FccEventField.CAUSE.getWireName()).asText("") : "";
                    if ("NO_ANSWER".equalsIgnoreCase(cause)) {
                        log.warn("⚠️ [转接话道事件] 忽略话道 A 的伪超时挂断事件 cause=NO_ANSWER callId={}", call.getCallId());
                        return true;
                    }
                    log.warn("⚠️ [转接中坐席挂机] 坐席 A 在转接过程中主动挂机 cause={} callId={}", cause, call.getCallId());
                    cancelTransferOnAgentHangup(call);
                    return true; // 拦截事件，避免漏给业务模型误触发客户满意度评价！
                }
                return true;
            }
            if (Boolean.TRUE.equals(call.getData().get("transferSuccess"))) {
                // 原坐席 A 已在转接成功时被释放，忽略 A 话道的后续事件（如 DESTROY），避免破坏 B 与 C 正在进行的通话
                log.info("ℹ️ [转接话道事件] 消费原坐席 A 的后续事件 state={} callId={}", state, call.getCallId());
                return true;
            }
        }

        // 3. 转接中话道 B 的事件
        String uuidB = (String) call.getData().get("transferGuestUuid");
        if (uuidB != null && uuidB.equals(channelUuid) && Boolean.TRUE.equals(call.getData().get("transferPending"))) {
            if (state == ChannelEventState.UNBRIDGE || state == ChannelEventState.READY) {
                return true; // 忽略 park / unbridge 事件
            }
            if (state == ChannelEventState.DESTROY) {
                String cause = params != null ? params.path(FccEventField.CAUSE.getWireName()).asText("") : "";
                log.warn("👋 [转接中客户挂机] 客户 B 在转接过程中主动挂机 cause={} callId={}", cause, call.getCallId());
                cancelTransferOnGuestHangup(call);
                return true;
            }
            return true;
        }

        return false;
    }

    /**
     * 转接目标 C 应答，执行成功流程：
     * 停止 A、B 放音，恢复 hangup_after_bridge，桥接 B 与 C，挂断原坐席 A
     */
    private void handleTransferAnswer(CallInfoBO call, String uuidC) {
        synchronized (call) {
            if (!Boolean.TRUE.equals(call.getData().get("transferPending"))) {
                return;
            }
            call.putData("transferPending", false);
            call.putData("transferSuccess", true);
            String uuidA = (String) call.getData().get("transferAgentUuid");
            String uuidB = (String) call.getData().get("transferGuestUuid");
            String target = (String) call.getData().get("transferTarget");

            log.info("🎉 [转接成功] 目标分机 {} 已应答！停止回铃放音，桥接客户 B({}) 与目标 C({})，释放原坐席 A({})",
                target, uuidB, uuidC, uuidA);

            // 1. 停止 A 和 B 的回铃放音
            client.nativeAPI("uuid_break", uuidA + " all");
            client.nativeAPI("uuid_break", uuidB + " all");

            // 2. 恢复 B 和 C 的 hangup_after_bridge
            client.nativeAPI("uuid_setvar", uuidB + " hangup_after_bridge true");
            client.nativeAPI("uuid_setvar", uuidC + " hangup_after_bridge true");

            // 3. 桥接 B 与 C
            client.channelBridge(call.getCtrlId(), uuidB, uuidC);

            // 4. 释放原坐席 A 并将其置为 ACW 话后整理
            client.hangup(call.getCtrlId(), uuidA, "NORMAL_CLEARING");
            if (call.getAgentWorkNo() != null && call.getCallId() != null) {
                try {
                    agents.release(call.getAgentWorkNo(), call.getCallId());
                } catch (Exception e) {
                    log.error("释放原坐席进入ACW失败: {}", e.getMessage(), e);
                }
            }
            websocket.pushCallHangup(
                call.getAgentWorkNo(),
                call.getCallId(),
                Map.of("cause", "TRANSFERRED", "message", "通话已成功转接至分机 " + target)
            );

            // 5. 更新通话上下文中的坐席话道为 C
            call.setAgentChannelUuid(uuidC);
            call.getData().remove("transferChannelUuid");
            persistence.saveOrUpdateSession(call);
        }
    }

    /**
     * 转接目标 C 未接听 (超时/拒接/挂断)，执行回退流程：
     * 停止 A、B 放音，恢复 hangup_after_bridge，重新桥接 A 与 B，并通知坐席
     */
    private void handleTransferFailure(CallInfoBO call, String cause) {
        synchronized (call) {
            if (!Boolean.TRUE.equals(call.getData().get("transferPending"))) {
                return;
            }
            call.putData("transferPending", false);
            call.putData("transferFailed", true);
            String uuidA = (String) call.getData().get("transferAgentUuid");
            String uuidB = (String) call.getData().get("transferGuestUuid");
            String uuidC = (String) call.getData().get("transferChannelUuid");
            String target = (String) call.getData().get("transferTarget");

            log.warn("⚠️ [转接未接听回退] 目标分机 {} 未接听 ({})，停止回铃，自动重新桥接坐席 A({}) 与客户 B({})",
                target, cause, uuidA, uuidB);

            // 1. 停止 A 和 B 的回铃放音
            client.nativeAPI("uuid_break", uuidA + " all");
            client.nativeAPI("uuid_break", uuidB + " all");

            // 2. 确保 C 话道已拆除
            if (uuidC != null) {
                try {
                    client.hangup(call.getCtrlId(), uuidC, "NORMAL_CLEARING");
                } catch (Exception ignored) {}
            }

            // 3. 恢复 A 和 B 的 hangup_after_bridge
            client.nativeAPI("uuid_setvar", uuidA + " hangup_after_bridge true");
            client.nativeAPI("uuid_setvar", uuidB + " hangup_after_bridge true");

            // 4. 重新桥接 A 与 B
            client.channelBridge(call.getCtrlId(), uuidA, uuidB);

            // 5. 清理转接标识
            call.getData().remove("transferChannelUuid");
            call.putData("transferFailedReason", cause);
            persistence.saveOrUpdateSession(call);

            // 6. WebSocket 通知坐席转接未接听，已恢复通话
            websocket.pushCallAnswered(
                call.getAgentWorkNo(),
                call.getCallId(),
                Map.of(
                    "callId", call.getCallId(),
                    "status", "TRANSFER_FALLBACK",
                    "message", "目标分机 " + target + " 超时未接听，已恢复与客户通话"
                )
            );
        }
    }

    /**
     * 坐席在转接中主动挂机
     */
    private void cancelTransferOnAgentHangup(CallInfoBO call) {
        synchronized (call) {
            call.putData("transferPending", false);
            call.putData("transferAborted", true);
            String uuidB = (String) call.getData().get("transferGuestUuid");
            String uuidC = (String) call.getData().get("transferChannelUuid");
            if (uuidB != null) {
                client.nativeAPI("uuid_break", uuidB + " all");
                try {
                    client.hangup(call.getCtrlId(), uuidB, "NORMAL_CLEARING");
                } catch (Exception ignored) {}
            }
            if (uuidC != null) {
                try {
                    client.hangup(call.getCtrlId(), uuidC, "NORMAL_CLEARING");
                } catch (Exception ignored) {}
            }
            if (call.getAgentWorkNo() != null && call.getCallId() != null) {
                try {
                    agents.release(call.getAgentWorkNo(), call.getCallId());
                } catch (Exception e) {
                    log.error("释放主动挂机坐席失败: {}", e.getMessage(), e);
                }
            }
            call.getData().remove("transferChannelUuid");
            persistence.saveOrUpdateSession(call);
        }
    }

    /**
     * 客户在转接中主动挂机
     */
    private void cancelTransferOnGuestHangup(CallInfoBO call) {
        synchronized (call) {
            call.putData("transferPending", false);
            call.putData("transferAborted", true);
            String uuidA = (String) call.getData().get("transferAgentUuid");
            String uuidC = (String) call.getData().get("transferChannelUuid");
            if (uuidA != null) {
                client.nativeAPI("uuid_break", uuidA + " all");
                try {
                    client.hangup(call.getCtrlId(), uuidA, "NORMAL_CLEARING");
                } catch (Exception ignored) {}
                if (call.getAgentWorkNo() != null && call.getCallId() != null) {
                    try {
                        agents.release(call.getAgentWorkNo(), call.getCallId());
                    } catch (Exception e) {
                        log.error("释放坐席进入ACW失败: {}", e.getMessage(), e);
                    }
                    websocket.pushCallHangup(
                        call.getAgentWorkNo(),
                        call.getCallId(),
                        Map.of("cause", "GUEST_HANGUP", "message", "客户已挂机，转接终止")
                    );
                }
            }
            if (uuidC != null) {
                try {
                    client.hangup(call.getCtrlId(), uuidC, "NORMAL_CLEARING");
                } catch (Exception ignored) {}
            }
            call.getData().remove("transferChannelUuid");
            persistence.saveOrUpdateSession(call);
        }
    }

    /**
     * 31秒看门狗超时检查
     */
    private void handleTransferTimeout(String callId) {
        sessions.getByCallId(callId).ifPresent(call -> {
            synchronized (call) {
                if (Boolean.TRUE.equals(call.getData().get("transferPending"))) {
                    log.warn("⏰ [转接看门狗] 30s超时未收到目标应答，触发自动回退 callId={}", callId);
                    handleTransferFailure(call, "TIMEOUT_30S");
                }
            }
        });
    }
}
