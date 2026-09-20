package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.enums.FlowActionExecutorType;

/**
 * 一类受控流程动作的执行策略。
 */
public interface FlowActionExecutor {

    /**
     * 返回该实现唯一负责的执行器类型。
     *
     * @return 执行器类型
     */
    FlowActionExecutorType type();

    /**
     * 执行已经过公共动作目录校验的调用。
     *
     * @param context 动作上下文
     * @return 即时执行结果
     */
    FlowActionResult execute(FlowActionContext context);
}
