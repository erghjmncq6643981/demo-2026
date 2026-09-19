package com.chandler.fcc.server.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.server.flow.AbstractCallStageProcessor;
import com.chandler.fcc.server.flow.event.CallConnectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通话接通阶段处理器 (CONNECTED)
 * <p>
 * 响应 {@link CallConnectedEvent}，当双方话道成功桥接对讲后触发双轨录音启动等操作。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallConnectedProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallConnectedEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.CONNECTED;
    }

    @Override
    public void onApplicationEvent(CallConnectedEvent event) {
        CallInfoBO call = event.getSource();
        log.info("🔗 [阶段流转 -> CONNECTED] CallID: {}, CtrlID: {}, Model: {}",
                call.getCallId(), call.getCtrlId(), call.getModelKey());

        List<FlowNode> nodes = getFlowNodes(call.getModelKey());
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
