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

    /**
     * 返回执行器支持的动作类型。
     * @return 业务动作类型
     */
    @Override
    public ActionType getActionType() {
        return ActionType.CHANNEL_BRIDGE;
    }

    /**
     * 根据明确的运行时标识执行呼叫动作。
     * @param callUuid 业务通话标识，不作为话道或控制标识
     * @param flowUuid 流程步骤实例标识
     * @param flowNode 包含独立控制、话道及动作参数的节点
     * @throws IllegalArgumentException 必需参数缺失时抛出
     */
    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String uuidA = requiredData(flowNode, "guestChannelUuid");
        String uuidB = requiredData(flowNode, "agentChannelUuid");
        String ctrlUuid = requiredData(flowNode, "ctrlId");

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
