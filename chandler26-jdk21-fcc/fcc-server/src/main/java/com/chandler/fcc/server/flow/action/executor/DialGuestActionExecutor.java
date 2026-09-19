package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 外呼客户动作执行器 (DIAL_GUEST)
 * <p>
 * 向外部客户手机号或内部测试分机发起呼叫，并将客户话道 UUID 关联至当前通话上下文。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialGuestActionExecutor extends AbstractFccActionExecutor {

    private final CallSessionManager sessionManager;

    @Override
    public ActionType getActionType() {
        return ActionType.DIAL_GUEST;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String destNumber = flowNode.getDataStr("destNumber", "1008");
        String callerNumber = flowNode.getDataStr("callerNumber", "02150880000");
        String ctrlUuid = flowNode.getDataStr("ctrlUuid", callUuid);
        String guestChannelUuid = flowNode.getDataStr("guestChannelUuid", null);

        if (guestChannelUuid == null || guestChannelUuid.trim().isEmpty()) {
            guestChannelUuid = IdUtil.getUuid();
        }

        // 绑定客户通道与控制会话
        sessionManager.bindChannel(guestChannelUuid, ctrlUuid);
        final String finalGuestUuid = guestChannelUuid;
        sessionManager.getByCtrlUuid(ctrlUuid).ifPresent(s -> {
            s.setGuestChannelUuid(finalGuestUuid);
            s.setDestinationNumber(destNumber);
            s.putData("guestChannelUuid", finalGuestUuid);
            s.putData("guestDialed", "true");
        });

        log.info("📞 [FCC 执行动作: 呼叫客户] CallUUID: {}, Dest: {}, Caller: {}, Channel: {}",
                callUuid, destNumber, callerNumber, guestChannelUuid);

        Map<String, String> channelVars = new HashMap<>();
        channelVars.put("hangup_after_bridge", "false");
        channelVars.put("park_after_bridge", "true");
        channelVars.put("absolute_codec_string", "PCMU,PCMA");
        channelVars.put("liberal_dtmf", "true");

        String dialStr = destNumber.contains("/") ? destNumber : "user/" + destNumber;
        FNodeDialDTO.CallParam callParam = FNodeDialDTO.CallParam.builder()
                .dialString(dialStr)
                .cidName("CustomerCall")
                .cidNumber(callerNumber)
                .uuid(guestChannelUuid)
                .params(channelVars)
                .build();

        FNodeDialDTO dialDTO = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(guestChannelUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(Collections.singletonList(callParam))
                        .build())
                .timeout(30)
                .build();

        FNodeResult result = getFccClient().dial(dialDTO);
        log.info("📥 [FCC 呼叫客户应答] Result: {}", result);
    }
}
