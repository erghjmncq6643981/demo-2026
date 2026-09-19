package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 话道双向桥接动作执行器 (CHANNEL_BRIDGE)
 * <p>
 * 将客户话道与坐席话道执行双向桥接，使双方进入实时通话状态。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBridgeActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.CHANNEL_BRIDGE;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String uuidA = flowNode.getDataStr("uuidA", null);
        String uuidB = flowNode.getDataStr("uuidB", null);
        String ctrlUuid = flowNode.getDataStr("ctrlUuid", callUuid);

        if (uuidA == null || uuidB == null) {
            log.error("❌ [FCC 桥接失败] 话道参数缺失: uuidA={}, uuidB={}", uuidA, uuidB);
            return;
        }

        log.info("🔗 [FCC 执行动作: 话道桥接] CallUUID: {}, CtrlUUID: {}, UUID-A: {}, UUID-B: {}",
                callUuid, ctrlUuid, uuidA, uuidB);

        FNodeResult result = getFccClient().channelBridge(ctrlUuid, uuidA, uuidB);
        log.info("📥 [FCC 话道桥接应答] Result: {}", result);
    }
}
