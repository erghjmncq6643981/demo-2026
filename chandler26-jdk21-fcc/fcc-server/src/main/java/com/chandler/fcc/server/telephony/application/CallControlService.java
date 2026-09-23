package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodeTransferDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 校验业务通话归属并下发逻辑话务控制命令。
 *
 * <p>命令应答只表示 Sidecar 已受理或拒绝，最终状态仍由标准话务事件推进。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallControlService {

    private final CallSessionManager sessions;
    private final FccClient client;
    private final FlowActionExecutionService actions;
    private final AgentIdentityService identity;

    /**
     * 取得当前坐席拥有的明确通话，禁止回落到全局最近会话。
     *
     * @param workNo 请求声明的坐席工号
     * @param callId 必填业务通话 ID
     * @return 当前坐席拥有的业务通话
     * @throws ResponseStatusException 身份、参数或通话归属校验失败
     */
    public CallInfoBO requireCall(String workNo, String callId) {
        var actor = identity.requirePrincipal();
        if (workNo != null && !workNo.isBlank() && !actor.workNo().equals(workNo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "坐席身份不匹配");
        }
        if (callId == null || callId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callId 必填");
        }
        CallInfoBO call = sessions
            .getByCallId(callId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "通话不存在")
            );
        if (!actor.workNo().equals(call.getAgentWorkNo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作此通话");
        }
        return call;
    }

    /**
     * 下发双方挂机命令，保留会话直至真实结束事件到达。
     *
     * @param call 已授权会话
     * @return 指令受理响应
     * @throws ResponseStatusException 话道缺失或 Sidecar 未受理
     */
    public Map<String, Object> hangup(CallInfoBO call) {
        var channels = new LinkedHashSet<String>();
        if (call.getGuestChannelUuid() != null) {
            channels.add(channel(call.getGuestChannelUuid()));
        }
        if (call.getAgentChannelUuid() != null) {
            channels.add(channel(call.getAgentChannelUuid()));
        }
        if (channels.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "话道尚未确认");
        }
        for (String uuid : channels) {
            actions.executeFNode(
                call,
                FlowActionType.HANGUP_CALL,
                FNodeHangupDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(uuid)
                    .cause("NORMAL_CLEARING")
                    .build(),
                "hangup-" + call.getCallId() + "-" + uuid
            );
        }
        return accepted(call, "挂机指令已受理，等待结束事件");
    }

    /**
     * 下发保持或恢复请求，不将命令应答解释为最终媒体状态。
     *
     * @param call 已授权会话
     * @param hold 是否进入保持
     * @return 指令受理响应
     * @throws ResponseStatusException 话道或命令执行校验失败
     */
    public Map<String, Object> hold(CallInfoBO call, boolean hold) {
        String uuid = channel(call.getGuestChannelUuid());
        requireAccepted(client.nativeAPI("uuid_hold", (hold ? "" : "off ") + uuid));
        return accepted(call, "保持指令已受理，媒体状态尚未确认");
    }

    /**
     * 向客户话道发送一个 DTMF 按键。
     *
     * @param call 已授权会话
     * @param digit 单个合法按键
     * @return 指令受理响应
     * @throws ResponseStatusException 按键、话道或命令执行校验失败
     */
    public Map<String, Object> dtmf(CallInfoBO call, String digit) {
        if (digit == null || !digit.matches("[0-9A-D*#]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效 DTMF 按键");
        }
        requireAccepted(
            client.nativeAPI(
                "uuid_send_dtmf",
                channel(call.getGuestChannelUuid()) + " " + digit
            )
        );
        return accepted(call, "DTMF 指令已受理");
    }

    /**
     * 盲转客户话道，不提前强拆原坐席或伪造挂机事件。
     *
     * @param call 已授权会话
     * @param target 目标号码
     * @return 指令受理响应
     * @throws ResponseStatusException 目标、话道或命令执行校验失败
     */
    public Map<String, Object> transfer(CallInfoBO call, String target) {
        if (target == null || !target.matches("[+0-9]{1,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效转接号码");
        }
        actions.executeFNode(
            call,
            FlowActionType.TRANSFER_CALL,
            FNodeTransferDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(channel(call.getGuestChannelUuid()))
                .target(target)
                .context("default")
                .build(),
            "transfer-" + call.getCallId()
        );
        return accepted(call, "转接指令已受理，等待话务事件确认");
    }

    /**
     * 校验 Sidecar 应答与受控原生命令结果。
     *
     * @param result Sidecar 应答
     * @throws ResponseStatusException 结果未知、Sidecar 拒绝或 FreeSWITCH 返回错误
     */
    public static void requireAccepted(FNodeResult result) {
        if (result == null || Integer.valueOf(-32000).equals(result.getCode())) {
            throw new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "指令结果未知，请核对通话状态，不要盲目重试"
            );
        }
        if (!result.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "软交换拒绝指令");
        }
        if (
            result.getData() instanceof Map<?, ?> data &&
            data.get("response") instanceof String response &&
            response.stripLeading().startsWith("-ERR")
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "软交换执行失败");
        }
    }

    /**
     * 校验 FreeSWITCH 话道 UUID。
     *
     * @param uuid 话道 UUID
     * @return 合法话道 UUID
     * @throws ResponseStatusException 话道尚未确认
     */
    private static String channel(String uuid) {
        if (uuid == null || !uuid.matches("[a-fA-F0-9-]{36}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "有效话道尚未确认");
        }
        return uuid;
    }

    /**
     * 生成受理响应并记录业务关联，不宣称最终状态。
     *
     * @param call 业务通话
     * @param message 可读结果
     * @return 标准受理响应
     */
    private Map<String, Object> accepted(CallInfoBO call, String message) {
        log.info("[话务控制] {} callId={}", message, call.getCallId());
        return Map.of(
            "code",
            200,
            "message",
            message,
            "data",
            Map.of("callId", call.getCallId(), "status", "ACCEPTED")
        );
    }
}
