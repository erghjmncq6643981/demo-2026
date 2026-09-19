package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.LinkedHashSet;

/** 校验通话归属并执行控制；最终通话状态仅由软交换事件推进。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallControlService {
    private final CallSessionManager sessions;
    private final FccClient client;
    private final AgentIdentityService identity;

    /** 取得当前坐席拥有的明确通话，禁止全局最近会话兜底。
     * @param workNo 声明的坐席工号
     * @param callId 必填业务通话 ID
     * @return 有明确节点归属的会话
     */
    public CallInfoBO requireCall(String workNo, String callId) {
        String authenticated = identity.requireAgent(workNo);
        if (callId == null || callId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callId 必填");
        CallInfoBO call = sessions.getByCallId(callId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通话不存在"));
        if (!authenticated.equals(call.getAgentWorkNo())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此通话");
        if (call.getNodeId() == null || call.getNodeId().isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "通话节点尚未确认");
        return call;
    }

    /** 下发双方挂机，保留会话直至真实结束事件到达。
     * @param call 已授权会话
     * @return 指令受理响应
     */
    public Map<String, Object> hangup(CallInfoBO call) {
        var channels = new LinkedHashSet<String>();
        if (call.getGuestChannelUuid() != null) channels.add(channel(call.getGuestChannelUuid()));
        if (call.getAgentChannelUuid() != null) channels.add(channel(call.getAgentChannelUuid()));
        if (channels.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "话道尚未确认");
        for (String uuid : channels) requireAccepted(client.hangup(call.getNodeId(), call.getCtrlId(), uuid, "NORMAL_CLEARING"));
        return accepted(call, "挂机指令已受理，等待结束事件");
    }

    /** 下发保持请求，不将命令应答解释为最终媒体状态。
     * @param call 已授权会话
     * @param hold 是否保持
     * @return 指令受理响应
     */
    public Map<String, Object> hold(CallInfoBO call, boolean hold) {
        String uuid = channel(call.getGuestChannelUuid());
        requireAccepted(client.nativeAPI(call.getNodeId(), "uuid_hold", (hold ? "" : "off ") + uuid));
        return accepted(call, "保持指令已受理，媒体状态尚未确认");
    }

    /** 由控制面单路径向客户话道发送 DTMF。
     * @param call 已授权会话
     * @param digit 单个合法按键
     * @return 受理响应
     */
    public Map<String, Object> dtmf(CallInfoBO call, String digit) {
        if (digit == null || !digit.matches("[0-9A-D*#]")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效 DTMF 按键");
        requireAccepted(client.nativeAPI(call.getNodeId(), "uuid_send_dtmf", channel(call.getGuestChannelUuid()) + " " + digit));
        return accepted(call, "DTMF 指令已受理");
    }

    /** 盲转客户话道，不提前强拆原坐席或伪造挂机事件。
     * @param call 已授权会话
     * @param target 目标号码
     * @return 受理响应
     */
    public Map<String, Object> transfer(CallInfoBO call, String target) {
        if (target == null || !target.matches("[+0-9]{1,32}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效转接号码");
        requireAccepted(client.nativeAPI(call.getNodeId(), "uuid_transfer", channel(call.getGuestChannelUuid()) + " " + target + " XML default"));
        return accepted(call, "转接指令已受理，等待话务事件确认");
    }

    /** 校验节点响应与 ESL 原文，超时为未知结果，不能返回成功。
     * @param result 节点应答
     */
    public static void requireAccepted(FNodeResult result) {
        if (result == null || Integer.valueOf(-32000).equals(result.getCode()))
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "指令结果未知，请核对通话状态，不要盲目重试");
        if (!result.isSuccess()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "软交换拒绝指令");
        if (result.getData() instanceof Map<?, ?> data && data.get("response") instanceof String response && response.stripLeading().startsWith("-ERR"))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "软交换执行失败");
    }

    /** 校验话道参数，避免将未校验文本拼接成原生命令。
     * @param uuid 话道 UUID
     * @return 合法话道 UUID
     */
    private static String channel(String uuid) {
        if (uuid == null || !uuid.matches("[a-fA-F0-9-]{36}")) throw new ResponseStatusException(HttpStatus.CONFLICT, "有效话道尚未确认");
        return uuid;
    }

    /** 生成受理响应并记录业务关联，不宣称最终状态。
     * @param call 业务通话
     * @param message 可读结果
     * @return 标准响应
     */
    private Map<String, Object> accepted(CallInfoBO call, String message) {
        log.info("[话务控制] {} callId={}, nodeId={}", message, call.getCallId(), call.getNodeId());
        return Map.of("code", 200, "message", message, "data", Map.of("callId", call.getCallId(), "status", "ACCEPTED"));
    }
}
