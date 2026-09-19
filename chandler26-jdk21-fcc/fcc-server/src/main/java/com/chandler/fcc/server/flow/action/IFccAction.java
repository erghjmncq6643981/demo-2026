package com.chandler.fcc.server.flow.action;

import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.enums.ActionType;

/**
 * FCC 底层通信控制动作统一执行接口
 * <p>
 * 每个实现类对应一个特定的 {@link ActionType} 动作（如 DIAL_AGENT, CHANNEL_BRIDGE, RECORD 等）。
 * </p>
 *
 * @author Chandler
 */
public interface IFccAction {

    /**
     * 获取当前处理器负责的动作类型枚举
     *
     * @return 动作类型枚举
     */
    ActionType getActionType();

    /**
     * 执行具体通信控制动作
     *
     * @param callUuid 业务通话唯一标识
     * @param flowUuid 步骤执行流水标识
     * @param flowNode 包含动作参数的流程节点模型
     */
    void execute(String callUuid, String flowUuid, FlowNode flowNode);
}
