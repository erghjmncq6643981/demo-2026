package com.chandler.fcc.server.flow;

import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.server.flow.action.DefaultActionExecutorsManager;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 抽象呼叫阶段处理器基类
 * <p>
 * 封装各阶段对流程编排规则库（FlowConfig）与动作总控（DefaultActionExecutorsManager）的公共调用。
 * </p>
 *
 * @author Chandler
 */
@Getter
public abstract class AbstractCallStageProcessor implements ICallStageProcessor {

    @Autowired
    private FlowConfig flowConfig;

    @Autowired
    private DefaultActionExecutorsManager actionExecutorsManager;

    /**
     * 根据业务模型获取当前阶段预置的动作节点列表
     *
     * @param modelKey 业务流转模式标识
     * @return 流程动作节点列表
     */
    protected List<FlowNode> getFlowNodes(String modelKey) {
        return flowConfig.getFlowNodes(getCallStage(), modelKey);
    }

    /**
     * 调度执行指定流程节点的动作
     *
     * @param call 通话聚合根上下文
     * @param node 流程节点
     */
    protected void executeAction(CallInfoBO call, FlowNode node) {
        actionExecutorsManager.publish(call, node);
    }
}
