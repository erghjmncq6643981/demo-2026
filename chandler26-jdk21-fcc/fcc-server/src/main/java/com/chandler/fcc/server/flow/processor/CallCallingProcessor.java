package com.chandler.fcc.server.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.server.flow.AbstractCallStageProcessor;
import com.chandler.fcc.server.flow.event.CallCallingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 呼叫中/振铃阶段处理器 (CALLING / RINGING)
 * <p>
 * 响应 {@link CallCallingEvent}，记录话道呼叫与振铃动作轨迹。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallCallingProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallCallingEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.CALLING;
    }

    @Override
    public void onApplicationEvent(CallCallingEvent event) {
        CallInfoBO call = event.getSource();
        log.info("🔔 [阶段流转 -> CALLING/RINGING] CallID: {}, CtrlID: {}, Model: {}",
                call.getCallId(), call.getCtrlId(), call.getModelKey());

        List<FlowNode> nodes = getFlowNodes(call.getModelKey());
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
