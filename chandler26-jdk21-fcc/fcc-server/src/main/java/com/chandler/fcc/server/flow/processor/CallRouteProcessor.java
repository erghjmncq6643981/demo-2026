package com.chandler.fcc.server.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.server.flow.AbstractCallStageProcessor;
import com.chandler.fcc.server.flow.event.CallRouteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 路由排队调度阶段处理器 (ROUTE)
 * <p>
 * 响应 {@link CallRouteEvent}，在单侧话道就绪后执行路由分流、技能组分配或呼叫对端话道。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallRouteProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallRouteEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.ROUTE;
    }

    @Override
    public void onApplicationEvent(CallRouteEvent event) {
        CallInfoBO call = event.getSource();
        log.info("🎯 [阶段流转 -> ROUTE] CallID: {}, CtrlID: {}, Model: {}",
                call.getCallId(), call.getCtrlId(), call.getModelKey());

        List<FlowNode> nodes = getFlowNodes(call.getModelKey());
        for (FlowNode node : nodes) {
            executeAction(call, node);
        }
    }
}
