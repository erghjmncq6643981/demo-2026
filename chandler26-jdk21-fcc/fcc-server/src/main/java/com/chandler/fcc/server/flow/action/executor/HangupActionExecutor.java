package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 挂机拆线动作执行器 (HANGUP)
 * <p>
 * 向 FreeSWITCH 下发拆线挂断信令，可指定挂断坐席侧、客户侧或整通呼叫。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HangupActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.HANGUP;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String targetUuid = flowNode.getDataStr("targetUuid", null);
        String ctrlUuid = flowNode.getDataStr("ctrlUuid", callUuid);
        String cause = flowNode.getDataStr("cause", "NORMAL_CLEARING");

        log.info("✂️ [FCC 执行动作: 挂机拆线] CallUUID: {}, TargetUUID: {}, Cause: {}",
                callUuid, targetUuid, cause);

        FNodeResult result = getFccClient().hangup(ctrlUuid, targetUuid, cause);
        log.info("📥 [FCC 挂机拆线应答] Result: {}", result);
    }
}
