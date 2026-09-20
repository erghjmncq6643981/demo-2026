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
 * 二次 DTMF 按键透传；班长干预尚未实现，明确拒绝执行。
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

    @Autowired
    private com.chandler.fcc.server.telephony.application.CallControlService controls;
    @Autowired
    private com.chandler.fcc.server.telephony.application.AgentIdentityService identity;
    @org.springframework.beans.factory.annotation.Value("${fcc.flow.reload-token:}")
    private String reloadToken;
    @Autowired
    private com.chandler.fcc.server.infrastructure.nats.FccProperties properties;
    @Autowired
    private com.chandler.fcc.server.telephony.application.OutboundCallService outboundCalls;

    /** 坐席外呼请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "坐席外呼请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallOutboundReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席工号，必须与登录主体一致")
        private String workNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "申请使用的外呼主叫号码")
        private String callerPhone;
        @io.swagger.v3.oas.annotations.media.Schema(description = "外呼被叫号码")
        private String calleePhone;
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席录入的客户姓名")
        private String customerName;
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席录入的客户单位")
        private String companyName;
    }

    /** 明确业务通话的挂机请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "明确业务通话的挂机请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallHangupReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席工号，必须与登录主体一致")
        private String workNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "必填业务通话标识，不是话道 UUID")
        private String callId;
        @io.swagger.v3.oas.annotations.media.Schema(description = "请求挂机原因")
        private String reason;
    }

    /** 明确业务通话的保持请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "明确业务通话的保持请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallHoldReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席工号，必须与登录主体一致")
        private String workNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "必填业务通话标识，不是话道 UUID")
        private String callId;
        @io.swagger.v3.oas.annotations.media.Schema(description = "是否请求保持，必填")
        private Boolean hold;
    }

    /** 明确业务通话的按键请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "明确业务通话的按键请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallDtmfReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席工号，必须与登录主体一致")
        private String workNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "必填业务通话标识，不是话道 UUID")
        private String callId;
        @io.swagger.v3.oas.annotations.media.Schema(description = "单个 DTMF 按键")
        private String digit;
    }

    /** 尚未开放的班长干预请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "尚未开放的班长干预请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallSuperviseReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "请求操作的班长工号")
        private String supervisorWorkNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "目标坐席工号")
        private String targetWorkNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "干预类型，当前均未开放")
        private String type; // SPY, COACH, BARGE, KILL
        @io.swagger.v3.oas.annotations.media.Schema(description = "必填业务通话标识，不是话道 UUID")
        private String callId;
    }

    /** 明确业务通话的转接请求。 */
    @io.swagger.v3.oas.annotations.media.Schema(description = "明确业务通话的转接请求")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallTransferReq {
        @io.swagger.v3.oas.annotations.media.Schema(description = "坐席工号，必须与登录主体一致")
        private String workNo;
        @io.swagger.v3.oas.annotations.media.Schema(description = "必填业务通话标识，不是话道 UUID")
        private String callId;
        @io.swagger.v3.oas.annotations.media.Schema(description = "转接目标号码")
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
        return Map.of("code",200,"message","外呼已受理，请先接听坐席话机", "data",outboundCalls.start(req.getWorkNo(),req.getCalleePhone()));
    }

    /**
     * 挂机拆线
     */
    @PostMapping("/hangup")
    @Operation(summary = "挂断通话", description = "坐席或客户挂断当前通话，释放信道并进入话后整理")
    public Map<String, Object> hangup(@RequestBody CallHangupReq req) {
        return controls.hangup(controls.requireCall(req.getWorkNo(), req.getCallId()));
    }

    /**
     * 通话保持 / 恢复
     */
    @PostMapping("/hold")
    @Operation(summary = "呼叫保持与恢复", description = "切换当前通话的保持静音态")
    public Map<String, Object> hold(@RequestBody CallHoldReq req) {
        if (req.getHold() == null) return fail("hold 必填");
        return controls.hold(controls.requireCall(req.getWorkNo(), req.getCallId()), req.getHold());
    }

    /**
     * 二次 DTMF 按键透传
     */
    @PostMapping("/dtmf")
    @Operation(summary = "发送二次DTMF", description = "通话中发送按键数字 (如查询分机或IVR导航)")
    public Map<String, Object> dtmf(@RequestBody CallDtmfReq req) {
        return controls.dtmf(controls.requireCall(req.getWorkNo(), req.getCallId()), req.getDigit());
    }

    /**
     * 班长席现场干预调度 (监听/耳语/强插/强拆)
     */
    @PostMapping("/supervise")
    @Operation(summary = "班长席干预控制", description = "班长主管对进行中通话进行监听(SPY)、耳语(COACH)、强插(BARGE)或强拆(KILL)")
    public Map<String, Object> supervise(@RequestBody CallSuperviseReq req) {
        identity.requireAgent(req.getSupervisorWorkNo());
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_IMPLEMENTED, "班长干预尚未实现，操作未执行");
    }

    /**
     * 呼叫盲转 / 坐席转接
     */
    @PostMapping("/transfer")
    @Operation(summary = "呼叫转接", description = "将当前通话的客户话道盲转至指定坐席工号或分机号")
    public Map<String, Object> transfer(@RequestBody CallTransferReq req) {
        return controls.transfer(controls.requireCall(req.getWorkNo(), req.getCallId()), req.getTargetNumber());
    }

    /**
     * 热重载指定话务编排流程
     */
    @Operation(summary = "热重载指定话务编排流程")
    @PostMapping("/flow/reload")
    public Map<String, Object> reloadFlow(@RequestParam("flowKey") String flowKey,
            @RequestHeader(value = "X-FCC-Reload-Token", required = false) String token) {
        if (reloadToken.isBlank() || token == null || !java.security.MessageDigest.isEqual(reloadToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "无权重载流程");
        }
        boolean ok = flowConfig != null && flowConfig.reloadFlow(flowKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", ok ? 200 : 500);
        result.put("message", ok ? "流程热重载成功: " + flowKey : "流程热重载失败");
        result.put("data", Map.of("flowKey", flowKey, "reloaded", ok));
        return result;
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
