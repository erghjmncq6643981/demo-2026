package com.chandler.fcc.server.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.common.enums.FlowModelType;
import com.chandler.fcc.server.flow.AbstractCallStageProcessor;
import com.chandler.fcc.server.flow.event.CallStartEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 呼叫开始阶段处理器 (START)
 * <p>
 * 响应 {@link CallStartEvent}，根据呼叫方向自动适配流程模型（如呼入进入客服流程，呼出进入双向外呼流程），
 * 并按序调度 START 阶段所定义的首步动作。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallStartProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallStartEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.START;
    }

    @Override
    public void onApplicationEvent(CallStartEvent event) {
        CallInfoBO call = event.getSource();
        String modelKey = call.getModelKey();
        if (modelKey == null) {
            modelKey = call.getDirection() == DirectionType.INBOUND
                    ? FlowModelType.INBOUND_CUSTOMER_SERVICE.name()
                    : FlowModelType.OUTBOUND_TWO_WAY_CALL.name();
            call.setModelKey(modelKey);
        }

        log.info("🚀 [阶段流转 -> START] CallID: {}, Model: {}, Direction: {}",
                call.getCallId(), modelKey, call.getDirection());

        List<FlowNode> nodes = getFlowNodes(modelKey);
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
