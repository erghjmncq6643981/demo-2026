package com.chandler.fcc.server.event.handler;

import com.chandler.fcc.common.protocol.FccDtmfSource;
import com.chandler.fcc.common.protocol.FccEventMethod;
import com.chandler.fcc.common.protocol.FccEventField;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校验并观察话道物理 DTMF 按键事件。
 *
 * <p>完整收号是 {@code FNode.ReadDTMF} 的最终指令结果，通过
 * {@code Event.CommandResult.result.dtmf} 推进业务；本处理器不得把逐键事件当成收号结果。</p>
 */
@Component
@Slf4j
public class DtmfEventHandler implements FccEventHandler {

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
     * 校验逐键事件契约并保留调试级可观测性。
     *
     * @param params DTMF 事件参数
     */
    @Override
    public void handle(JsonNode params) {
        String channelUuid = params.path(FccEventField.CHANNEL_UUID.getWireName()).asText(null);
        String digit = params.path(FccEventField.DIGIT.getWireName()).asText(null);
        String source = params.path(FccEventField.DTMF_SOURCE.getWireName()).asText(null);
        if (
            channelUuid == null ||
            digit == null ||
            digit.isBlank() ||
            !FccDtmfSource.KEY_PRESS.getWireValue().equals(source)
        ) {
            throw new IllegalArgumentException("DTMF 事件必须是带话道标识的物理按键事件");
        }
        log.debug("[DTMF 事件] 观察到物理按键 channelUuid={}", channelUuid);
    }
}
