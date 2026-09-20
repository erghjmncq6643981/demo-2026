package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.server.agent.application.PhoneBindingService;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.telephony.application.InboundCallService;
import com.chandler.fcc.server.telephony.application.OutboundCallService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将 DTMF 事件交给当前通话的固定运行模型处理。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DtmfEventHandler implements FccEventHandler {

    private final CallSessionManager sessions;
    private final InboundCallService inboundCalls;
    private final PhoneBindingService phoneBinding;
    private final OutboundCallService outboundCalls;

    /**
     * 判断是否为 DTMF 事件。
     *
     * @param method 标准事件方法名
     * @return 是否支持
     */
    @Override
    public boolean supports(FccEventMethod method) {
        return method == FccEventMethod.DTMF;
    }

    /**
     * 关联通话并按呼入、话机绑定、呼出顺序交给唯一运行模型处理。
     *
     * @param params DTMF 事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String controlId = params.path(FccEventField.CONTROL_ID.getWireName()).asText(null);
        String channelUuid = params.path(FccEventField.CHANNEL_UUID.getWireName()).asText(null);
        String digit = params.path(FccEventField.DIGIT.getWireName()).asText(null);
        if (digit == null || digit.isBlank() || "_none_".equalsIgnoreCase(digit)) return;

        CallInfoBO call = sessions
            .getByCtrlUuid(controlId)
            .or(() -> sessions.getByChannelUuid(channelUuid))
            .orElse(null);
        if (call == null) {
            log.warn("[DTMF 事件] 未找到业务通话 ctrlId={} channelUuid={}", controlId, channelUuid);
            return;
        }
        call.putData("flowEventId", params.path(FccEventField.EVENT_ID.getWireName()).asText());
        call.putData(
            "flowSourceTime",
            params.path(FccEventField.SOURCE_TIMESTAMP.getWireName()).asLong()
        );

        if (inboundCalls.digits(call, params)) return;
        if (phoneBinding.digits(call, digit)) return;
        if (outboundCalls.digits(call, digit)) return;

        log.warn("[DTMF 事件] 通话没有匹配固定运行模型 callId={}", call.getCallId());
    }
}
