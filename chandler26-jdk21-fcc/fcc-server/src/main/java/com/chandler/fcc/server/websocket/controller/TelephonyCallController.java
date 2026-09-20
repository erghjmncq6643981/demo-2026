package com.chandler.fcc.server.websocket.controller;

import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.telephony.application.CallControlService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.chandler.fcc.server.websocket.controller.req.CallDtmfReq;
import com.chandler.fcc.server.websocket.controller.req.CallHangupReq;
import com.chandler.fcc.server.websocket.controller.req.CallHoldReq;
import com.chandler.fcc.server.websocket.controller.req.CallOutboundReq;
import com.chandler.fcc.server.websocket.controller.req.CallSuperviseReq;
import com.chandler.fcc.server.websocket.controller.req.CallTransferReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 为坐席工作台提供人工外呼和通话中的实时控制接口。
 */
@RestController
@RequestMapping("/api/telephony/call")
@RequiredArgsConstructor
@Tag(name = "核心呼叫控制接口", description = "提供外呼、挂断、保持、DTMF 与转接等信令控制")
public class TelephonyCallController {

    private final FlowConfig flowConfig;
    private final CallControlService controls;
    private final AgentIdentityService identity;
    private final OutboundCallService outboundCalls;

    @Value("${fcc.flow.reload-token:}")
    private String reloadToken;

    /**
     * 发起坐席先振铃的人工外呼。
     *
     * @param request 外呼请求
     * @return 已持久化的通话受理结果
     */
    @PostMapping("/outbound")
    @Operation(summary = "发起外呼", description = "坐席工作台发起对外呼叫并等待坐席话机接听")
    public Map<String, Object> outbound(@RequestBody CallOutboundReq request) {
        return Map.of(
            "code",
            200,
            "message",
            "外呼已受理，请先接听坐席话机",
            "data",
            outboundCalls.start(request.getWorkNo(), request.getCalleePhone())
        );
    }

    /**
     * 挂断当前坐席有权控制的通话。
     *
     * @param request 挂机请求
     * @return 控制结果
     */
    @PostMapping("/hangup")
    @Operation(summary = "挂断通话", description = "挂断当前通话并进入话后整理")
    public Map<String, Object> hangup(@RequestBody CallHangupReq request) {
        return controls.hangup(controls.requireCall(request.getWorkNo(), request.getCallId()));
    }

    /**
     * 保持或恢复当前坐席有权控制的通话。
     *
     * @param request 保持请求
     * @return 控制结果
     */
    @PostMapping("/hold")
    @Operation(summary = "呼叫保持与恢复", description = "切换当前通话的保持状态")
    public Map<String, Object> hold(@RequestBody CallHoldReq request) {
        if (request.getHold() == null) return failure("hold 必填");
        return controls.hold(
            controls.requireCall(request.getWorkNo(), request.getCallId()),
            request.getHold()
        );
    }

    /**
     * 向当前坐席有权控制的通话发送单个 DTMF 按键。
     *
     * @param request 按键请求
     * @return 控制结果
     */
    @PostMapping("/dtmf")
    @Operation(summary = "发送二次 DTMF", description = "通话中发送单个按键")
    public Map<String, Object> dtmf(@RequestBody CallDtmfReq request) {
        return controls.dtmf(
            controls.requireCall(request.getWorkNo(), request.getCallId()),
            request.getDigit()
        );
    }

    /**
     * 明确拒绝尚未实现的班长监听、耳语、强插和强拆操作。
     *
     * @param request 班长干预请求
     * @return 当前不会返回结果
     * @throws ResponseStatusException 功能尚未实现
     */
    @PostMapping("/supervise")
    @Operation(summary = "班长席干预控制", description = "当前版本尚未开放班长干预")
    public Map<String, Object> supervise(@RequestBody CallSuperviseReq request) {
        identity.requireAgent(request.getSupervisorWorkNo());
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "班长干预尚未实现，操作未执行");
    }

    /**
     * 将当前坐席有权控制的通话转接到目标号码。
     *
     * @param request 转接请求
     * @return 控制结果
     */
    @PostMapping("/transfer")
    @Operation(summary = "呼叫转接", description = "将当前客户话道转接至指定工号或分机")
    public Map<String, Object> transfer(@RequestBody CallTransferReq request) {
        return controls.transfer(
            controls.requireCall(request.getWorkNo(), request.getCallId()),
            request.getTargetNumber()
        );
    }

    /**
     * 由管理端在流程发布提交后热重载指定流程。
     *
     * @param flowKey 流程业务键
     * @param token 服务间重载令牌
     * @return 重载结果
     */
    @Operation(summary = "热重载指定话务编排流程")
    @PostMapping("/flow/reload")
    public Map<String, Object> reloadFlow(
        @RequestParam("flowKey") String flowKey,
        @RequestHeader(value = "X-FCC-Reload-Token", required = false) String token
    ) {
        if (!validReloadToken(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权重载流程");
        }
        boolean reloaded = flowConfig.reloadFlow(flowKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", reloaded ? 200 : 500);
        result.put("message", reloaded ? "流程热重载成功: " + flowKey : "流程热重载失败");
        result.put("data", Map.of("flowKey", flowKey, "reloaded", reloaded));
        return result;
    }

    /**
     * 使用固定时序比较服务间令牌。
     *
     * @param token 请求令牌
     * @return 令牌有效时返回 {@code true}
     */
    private boolean validReloadToken(String token) {
        return !reloadToken.isBlank() &&
        token != null &&
        MessageDigest.isEqual(
            reloadToken.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 构造参数校验失败响应。
     *
     * @param message 失败原因
     * @return 统一失败响应
     */
    private Map<String, Object> failure(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 400);
        result.put("message", message);
        return result;
    }
}
