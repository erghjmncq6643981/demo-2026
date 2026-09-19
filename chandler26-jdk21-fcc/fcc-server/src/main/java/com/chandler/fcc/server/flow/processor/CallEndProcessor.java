package com.chandler.fcc.server.flow.processor;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.CallStageState;
import com.chandler.fcc.server.flow.AbstractCallStageProcessor;
import com.chandler.fcc.server.flow.event.CallEndEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 呼叫挂断结束阶段处理器 (NORMAL_END)
 * <p>
 * 响应 {@link CallEndEvent}，在首个话道挂机时触发停止录音、开启满意度评价收号或释放外呼资源。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
public class CallEndProcessor extends AbstractCallStageProcessor implements ApplicationListener<CallEndEvent> {

    @Override
    public CallStageState getCallStage() {
        return CallStageState.NORMAL_END;
    }

    @Override
    public void onApplicationEvent(CallEndEvent event) {
        CallInfoBO call = event.getSource();
        log.info("🏁 [阶段流转 -> NORMAL_END] CallID: {}, CtrlID: {}, Model: {}, Cause: {}",
                call.getCallId(), call.getCtrlId(), call.getModelKey(), call.getHangupCause());

        List<FlowNode> nodes = getFlowNodes(call.getModelKey());
        for (FlowNode node : nodes) {
            if (com.chandler.fcc.common.enums.ActionType.READ_DTMF.equals(node.getActionType())) {
                if ("true".equals(call.getData().get("guestEnded"))) {
                    log.info("ℹ️ [CallEndProcessor] 客户已先行挂机，跳过满意度评价收号");
                    continue;
                }
            }
            executeAction(call, node);
        }
    }
}
