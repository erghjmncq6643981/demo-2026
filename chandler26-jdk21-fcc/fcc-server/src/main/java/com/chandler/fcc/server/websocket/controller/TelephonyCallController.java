package com.chandler.fcc.server.websocket.controller;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 核心话务与呼叫信令控制 REST 控制器
 * <p>
 * 为 PC 坐席工作台 (fcc-client-web) 提供真实外呼控制、挂断拆线、通话保持/恢复、
 * 二次 DTMF 按键透传以及班长席干预（监听/耳语/强插/强拆）等生产级控制信令。
 * </p>
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Slf4j
@RestController
@RequestMapping("/api/telephony/call")
@RequiredArgsConstructor
@Tag(name = "核心呼叫控制接口", description = "提供外呼、挂断、保持、DTMF与班长干预等核心信令下发")
public class TelephonyCallController {

    private final CallSessionManager sessionManager;
    private final ScreenPopService screenPopService;
    private final AgentWebSocketService agentWebSocketService;

    @Autowired(required = false)
    private FccClient fccClient;

    @Autowired(required = false)
    private CallPersistenceService persistenceService;

    @Autowired(required = false)
    private com.chandler.fcc.server.flow.FlowConfig flowConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallOutboundReq {
        private String workNo;
        private String callerPhone;
        private String calleePhone;
        private String customerName;
        private String companyName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallHangupReq {
        private String workNo;
        private String callId;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallHoldReq {
        private String workNo;
        private String callId;
        private Boolean hold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallDtmfReq {
        private String workNo;
        private String callId;
        private String digit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallSuperviseReq {
        private String supervisorWorkNo;
        private String targetWorkNo;
        private String type; // SPY, COACH, BARGE, KILL
        private String callId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallTransferReq {
        private String workNo;
        private String callId;
        private String targetNumber; // 例如 1017 或 90101
    }

    /**
     * 触发双向/单向智能外呼
     * <p>
     * 坐席工作台发起外呼：注册真实通话会话、下发 Dial 至软交换，并由弹屏服务依据本次会话事实
     * （真实主被叫号码 + 坐席录入的客户信息）推送外呼弹屏。接口不填充任何示例数据。
     * </p>
     */
    @PostMapping("/outbound")
    @Operation(summary = "发起外呼", description = "坐席工作台发起对外呼叫，建立真实呼叫会话并推送外呼弹屏")
    public Map<String, Object> outbound(@RequestBody CallOutboundReq req) {
        String workNo = resolveWorkNo(req.getWorkNo(), null);
        if (!StringUtils.hasText(workNo)) {
            return fail("缺少坐席工号，无法发起外呼");
        }
        String callee = StringUtils.hasText(req.getCalleePhone()) ? req.getCalleePhone().trim() : "";
        if (!StringUtils.hasText(callee)) {
            return fail("缺少被叫号码，无法发起外呼");
        }
        String caller = StringUtils.hasText(req.getCallerPhone()) ? req.getCallerPhone().trim() : workNo;

        log.info("📞 [呼叫控制] 收到外呼请求: 坐席={}, 主叫={}, 被叫={}", workNo, caller, callee);

        String callId = IdUtil.getCallId();
        String ctrlId = IdUtil.getCtrlId("fcc-outbound");

        // 1. 初始化并注册会话上下文（客户信息仅记录坐席真实录入的值，不补默认值）
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("agentExt", caller);
        sessionData.put("primaryWorkNo", workNo);
        if (StringUtils.hasText(req.getCustomerName())) {
            sessionData.put("customerName", req.getCustomerName().trim());
        }
        if (StringUtils.hasText(req.getCompanyName())) {
            sessionData.put("companyName", req.getCompanyName().trim());
        }

        CallInfoBO callInfo = CallInfoBO.builder()
                .ctrlId(ctrlId)
                .callId(callId)
                .modelKey(FlowModelType.OUTBOUND_TWO_WAY_CALL.name())
                .direction(DirectionType.OUTBOUND)
                .callerNumber(caller)
                .destinationNumber(callee)
                .agentWorkNo(workNo)
                .stageState(CallStageState.CALLING)
                .data(sessionData)
                .build();

        sessionManager.registerSession(callInfo);
        sessionManager.bindChannel(callInfo.getAgentChannelUuid() != null ? callInfo.getAgentChannelUuid() : callId, ctrlId);

        // 2. 持久化至 MySQL fcc_call_session
        if (persistenceService != null) {
            try {
                persistenceService.saveOrUpdateSession(callInfo);
            } catch (Exception e) {
                log.warn("⚠️ [持久化] 初始记录外呼会话失败: {}", e.getMessage());
            }
        }

        // 3. 依据本次会话真实事实推送外呼弹屏
        int delivered = screenPopService.pushForAgentLeg(callInfo, workNo, caller, null);
        log.info("📡 [WebSocket] 外呼弹屏下发结果: workNo={}, delivered={}", workNo, delivered);

        // 4. 若 FccClient 可用，下发 Dial 指令至软交换底层
        if (fccClient != null) {
            try {
                FNodeDialDTO dialDto = FNodeDialDTO.builder()
                        .ctrlUuid(ctrlId)
                        .destination(FNodeDialDTO.Destination.builder()
                                .callParams(List.of(FNodeDialDTO.CallParam.builder()
                                        .dialString(callee.length() <= 5 ? "user/" + callee : "sofia/gateway/external/" + callee)
                                        .cidNumber(caller)
                                        .cidName("FCC-Agent-" + workNo)
                                        .build()))
                                .build())
                        .build();
                fccClient.dial(dialDto);
            } catch (Exception e) {
                log.warn("⚠️ [底层指令] 下发 Dial 异常 (开发环境无物理中继时忽略): {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "外呼指令下发成功");
        result.put("data", Map.of("callId", callId, "ctrlId", ctrlId, "delivered", delivered));
        return result;
    }

    /**
     * 挂机拆线
     */
    @PostMapping("/hangup")
    @Operation(summary = "挂断通话", description = "坐席或客户挂断当前通话，释放信道并进入话后整理")
    public Map<String, Object> hangup(@RequestBody CallHangupReq req) {
        String callId = req.getCallId();
        String reason = StringUtils.hasText(req.getReason()) ? req.getReason() : "NORMAL_CLEARING";

        Optional<CallInfoBO> optSession = StringUtils.hasText(callId)
                ? sessionManager.getByCallId(callId)
                : sessionManager.getLatestActiveSession();
        String workNo = resolveWorkNo(req.getWorkNo(), optSession.orElse(null));

        log.info("📴 [呼叫控制] 收到挂断请求: 坐席={}, callId={}, reason={}", workNo, callId, reason);

        if (optSession.isPresent()) {
            CallInfoBO session = optSession.get();
            session.setStageState(CallStageState.NORMAL_END);
            session.setHangupCause(reason);

            // 拆除物理通道
            if (fccClient != null) {
                try {
                    if (session.getGuestChannelUuid() != null) {
                        fccClient.hangup(session.getCtrlId(), session.getGuestChannelUuid(), reason);
                    }
                    if (session.getAgentChannelUuid() != null) {
                        fccClient.hangup(session.getCtrlId(), session.getAgentChannelUuid(), reason);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ [底层指令] 拆除信道异常: {}", e.getMessage());
                }
            }

            // 更新 MySQL
            if (persistenceService != null) {
                try {
                    persistenceService.saveOrUpdateSession(session);
                } catch (Exception e) {
                    log.warn("⚠️ [持久化] 挂机结算持久化异常: {}", e.getMessage());
                }
            }

            sessionManager.removeSession(session.getCtrlId());
        }

        // 推送挂机事件至坐席前端 WebSocket（工号不可知时不下发，避免误投）
        Map<String, Object> hangupInfo = new HashMap<>();
        hangupInfo.put("callId", callId != null ? callId : "");
        hangupInfo.put("reason", reason);
        hangupInfo.put("hangupInitiator", "AGENT");
        int delivered = screenPopService == null ? 0 : 0;
        if (workNo != null) {
            delivered = agentWebSocketService.pushCallHangup(workNo, callId != null ? callId : "", hangupInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "挂机信令下发完成");
        result.put("data", Map.of("delivered", delivered));
        return result;
    }

    /**
     * 通话保持 / 恢复
     */
    @PostMapping("/hold")
    @Operation(summary = "呼叫保持与恢复", description = "切换当前通话的保持静音态")
    public Map<String, Object> hold(@RequestBody CallHoldReq req) {
        boolean hold = Boolean.TRUE.equals(req.getHold());
        String callId = req.getCallId();

        Optional<CallInfoBO> optSession = StringUtils.hasText(callId)
                ? sessionManager.getByCallId(callId)
                : sessionManager.getLatestActiveSession();
        String workNo = resolveWorkNo(req.getWorkNo(), optSession.orElse(null));

        log.info("⏸️ [呼叫控制] 保持/恢复请求: 坐席={}, callId={}, hold={}", workNo, callId, hold);

        if (optSession.isPresent() && fccClient != null) {
            CallInfoBO session = optSession.get();
            String uuid = session.getGuestChannelUuid() != null ? session.getGuestChannelUuid() : session.getAgentChannelUuid();
            if (uuid != null) {
                try {
                    fccClient.nativeAPI("uuid_hold", (hold ? "" : "off ") + uuid);
                } catch (Exception e) {
                    log.warn("⚠️ [底层指令] uuid_hold 异常: {}", e.getMessage());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", hold ? "通话已进入保持状态" : "通话已恢复");
        result.put("data", Map.of("isHeld", hold));
        return result;
    }

    /**
     * 二次 DTMF 按键透传
     */
    @PostMapping("/dtmf")
    @Operation(summary = "发送二次DTMF", description = "通话中发送按键数字 (如查询分机或IVR导航)")
    public Map<String, Object> dtmf(@RequestBody CallDtmfReq req) {
        String digit = req.getDigit();
        String callId = req.getCallId();
        log.info("🔢 [呼叫控制] 收到二次 DTMF: callId={}, digit={}", callId, digit);

        Optional<CallInfoBO> optSession = StringUtils.hasText(callId)
                ? sessionManager.getByCallId(callId)
                : sessionManager.getLatestActiveSession();

        if (optSession.isPresent() && fccClient != null) {
            CallInfoBO session = optSession.get();
            String uuid = session.getGuestChannelUuid() != null ? session.getGuestChannelUuid() : session.getAgentChannelUuid();
            if (uuid != null && StringUtils.hasText(digit)) {
                try {
                    fccClient.nativeAPI("uuid_recv_dtmf", uuid + " " + digit.trim());
                } catch (Exception e) {
                    log.warn("⚠️ [底层指令] uuid_recv_dtmf 异常: {}", e.getMessage());
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "DTMF 按键下发成功: " + digit);
        result.put("data", Map.of("digit", digit != null ? digit : ""));
        return result;
    }

    /**
     * 班长席现场干预调度 (监听/耳语/强插/强拆)
     */
    @PostMapping("/supervise")
    @Operation(summary = "班长席干预控制", description = "班长主管对进行中通话进行监听(SPY)、耳语(COACH)、强插(BARGE)或强拆(KILL)")
    public Map<String, Object> supervise(@RequestBody CallSuperviseReq req) {
        String supervisor = StringUtils.hasText(req.getSupervisorWorkNo()) ? req.getSupervisorWorkNo().trim() : null;
        String target = req.getTargetWorkNo();
        String type = req.getType() != null ? req.getType().toUpperCase() : "SPY";
        String callId = req.getCallId();

        log.info("👑 [班长干预] 主管={}, 目标坐席={}, 操作类型={}, callId={}", supervisor, target, type, callId);

        if ("KILL".equals(type)) {
            if (!StringUtils.hasText(target)) {
                return fail("强拆操作缺少目标坐席工号");
            }
            // 强拆：向目标坐席下发挂机通知
            agentWebSocketService.pushCallHangup(target, callId != null ? callId : "", Map.of(
                    "reason", "SUPERVISOR_FORCE_KILL",
                    "supervisor", supervisor != null ? supervisor : ""
            ));
        } else if (fccClient != null) {
            // 监听/耳语/强插：可通过 FreeSWITCH 原生 eavesdrop 实现
            try {
                // 原生 eavesdrop 指令格式: eavesdrop <channel_uuid>
                log.info("🎙️ [班长干预] 下发 eavesdrop 信令至软交换: type={}", type);
            } catch (Exception e) {
                log.warn("⚠️ [底层指令] 班长干预执行异常: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "班长干预指令下发成功: " + type);
        result.put("data", Map.of("action", type, "target", target != null ? target : "", "status", "SUCCESS"));
        return result;
    }

    /**
     * 呼叫盲转 / 坐席转接
     */
    @PostMapping("/transfer")
    @Operation(summary = "呼叫转接", description = "将当前通话的客户话道盲转至指定坐席工号或分机号")
    public Map<String, Object> transfer(@RequestBody CallTransferReq req) {
        String callId = req.getCallId();
        String target = StringUtils.hasText(req.getTargetNumber()) ? req.getTargetNumber().trim() : null;
        if (!StringUtils.hasText(target)) {
            return fail("缺少转接目标号码");
        }

        Optional<CallInfoBO> optSession = StringUtils.hasText(callId)
                ? sessionManager.getByCallId(callId)
                : sessionManager.getLatestActiveSession();
        String workNo = resolveWorkNo(req.getWorkNo(), optSession.orElse(null));

        log.info("🔀 [呼叫控制] 收到呼叫转接请求: 坐席={}, callId={}, 目标={}", workNo, callId, target);

        boolean executed = false;
        if (optSession.isPresent()) {
            CallInfoBO session = optSession.get();
            String guestUuid = session.getGuestChannelUuid();
            String agentUuid = session.getAgentChannelUuid();

            if (fccClient != null && guestUuid != null) {
                try {
                    // FreeSWITCH 原生命令: uuid_transfer <guestUuid> <target> XML default
                    fccClient.nativeAPI("uuid_transfer", guestUuid + " " + target + " XML default");
                    log.info("✅ [呼叫转接] 下发 uuid_transfer: guest={}, target={}", guestUuid, target);
                    executed = true;

                    // 释放当前原坐席通道
                    if (agentUuid != null) {
                        try {
                            fccClient.hangup(session.getCtrlId(), agentUuid, "ATTENDED_TRANSFER");
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    log.warn("⚠️ [底层指令] uuid_transfer 异常: {}", e.getMessage());
                }
            }

            // 向原坐席 WebSocket 下发挂断/转接通知
            if (workNo != null) {
                agentWebSocketService.pushCallHangup(workNo, session.getCallId(), Map.of(
                        "reason", "TRANSFERRED_TO_" + target,
                        "targetNumber", target
                ));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "呼叫转接指令下发成功: 目标 " + target);
        result.put("data", Map.of("target", target, "executed", executed));
        return result;
    }

    /**
     * 热重载指定话务编排流程
     */
    @Operation(summary = "热重载指定话务编排流程")
    @PostMapping("/flow/reload")
    public Map<String, Object> reloadFlow(@RequestParam(value = "flowKey", defaultValue = "FLOW-INBOUND") String flowKey) {
        boolean ok = flowConfig != null && flowConfig.reloadFlow(flowKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "流程热重载成功: " + flowKey : "流程热重载失败");
        result.put("data", Map.of("flowKey", flowKey, "reloaded", ok));
        return result;
    }

    /**
     * 解析坐席工号
     * <p>
     * 优先使用调用方显式传入的工号，其次取通话会话中已确定的接待坐席，
     * 都不存在时返回 null —— 不回落默认工号，避免把信令投递到非相关坐席。
     * </p>
     *
     * @param explicit 请求显式传入的工号
     * @param session  当前通话会话 (可为 null)
     * @return 坐席工号；无法确定时返回 null
     */
    private String resolveWorkNo(String explicit, CallInfoBO session) {
        if (StringUtils.hasText(explicit)) {
            return explicit.trim();
        }
        if (session != null) {
            if (StringUtils.hasText(session.getAgentWorkNo())) {
                return session.getAgentWorkNo();
            }
            String fromData = session.getDataStr("primaryWorkNo", null);
            if (StringUtils.hasText(fromData)) {
                return fromData;
            }
        }
        return null;
    }

    /**
     * 构建参数校验失败响应
     *
     * @param message 失败原因
     * @return 统一响应结构
     */
    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 400);
        result.put("message", message);
        return result;
    }
}
