package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.FccCommandResultStatus;
import com.chandler.fcc.server.agent.application.PhoneBindingService;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 持久化并分发 Sidecar 异步指令最终结果。
 *
 * <p>本处理器只处理命令完成事实。话道应答、桥接、挂机等状态仍由
 * {@link ChannelEventHandler} 基于 {@code Event.Channel} 独立推进。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandResultEventHandler implements FccEventHandler {

    private final CallSessionManager sessions;
    private final CallPersistenceService persistence;
    private final PhoneBindingService phoneBinding;
    private final InboundCallService inboundCalls;
    private final OutboundCallService outboundCalls;

    /**
     * 判断是否为指令最终结果事件。
     *
     * @param method 规范事件方法
     * @return 指令结果事件返回 {@code true}
     */
    @Override
    public boolean supports(FccEventMethod method) {
        return method == FccEventMethod.COMMAND_RESULT;
    }

    /**
     * 更新命令审计，并将属于活跃通话的结果交给固定运行模型。
     *
     * @param params 规范指令结果参数
     */
    @Override
    public void handle(JsonNode params) {
        String commandId = text(params, FccEventField.COMMAND_ID);
        String commandMethod = text(params, FccEventField.COMMAND_METHOD);
        String nodeId = text(params, FccEventField.NODE_ID);
        FccCommandResultStatus commandStatus = FccCommandResultStatus.fromWireValue(
            text(params, FccEventField.COMMAND_STATUS)
        );
        String controlId = text(params, FccEventField.CONTROL_ID);
        String channelUuid = text(params, FccEventField.CHANNEL_UUID);
        if (
            commandId == null || commandMethod == null || nodeId == null
        ) {
            throw new IllegalArgumentException("指令结果缺少身份字段或最终状态不合法");
        }
        boolean succeeded = commandStatus == FccCommandResultStatus.SUCCEEDED;
        int updated = persistence.completeCommand(
            commandId,
            commandMethod,
            nodeId,
            controlId,
            channelUuid,
            succeeded ? "SUCCESS" : "FAILED",
            succeeded ? null : text(params, FccEventField.COMMAND_CODE),
            text(params, FccEventField.COMMAND_MESSAGE),
            params.toString()
        );
        if (updated == 0) {
            throw new IllegalStateException("指令结果未找到对应审计记录: " + commandId);
        }

        CallInfoBO call = sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .orElse(null);
        if (call == null) {
            log.info("[指令结果] 活跃通话已结束 commandId={} status={}", commandId, commandStatus.getWireValue());
            return;
        }
        if (
            channelUuid == null ||
            (!channelUuid.equals(call.getGuestChannelUuid()) &&
                !channelUuid.equals(call.getAgentChannelUuid())) ||
            (call.getNodeId() != null && !call.getNodeId().equals(nodeId))
        ) {
            throw new IllegalArgumentException("指令结果话道或节点不属于关联通话");
        }
        call.putData("flowEventId", text(params, FccEventField.EVENT_ID));
        call.putData(
            "flowSourceTime",
            params.path(FccEventField.SOURCE_TIMESTAMP.getWireName()).asLong()
        );
        if (phoneBinding.commandResult(call, params)) {
            return;
        }
        if (inboundCalls.commandResult(call, params)) {
            return;
        }
        if (outboundCalls.commandResult(call, params)) {
            return;
        }
        log.debug("[指令结果] 通话无需业务推进 callId={} commandId={}", call.getCallId(), commandId);
    }

    /**
     * 读取可选文本字段。
     *
     * @param node JSON 参数对象
     * @param field 规范字段
     * @return 文本值，字段缺失时返回空
     */
    private String text(JsonNode node, FccEventField field) {
        return node.hasNonNull(field.getWireName())
            ? node.get(field.getWireName()).asText()
            : null;
    }
}
