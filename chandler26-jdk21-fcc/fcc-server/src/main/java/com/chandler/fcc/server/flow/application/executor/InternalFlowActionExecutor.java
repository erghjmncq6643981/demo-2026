package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.enums.FlowActionExecutorType;
import org.springframework.stereotype.Component;

/**
 * 执行由明确业务服务提供的内部方法调用。
 */
@Component
public class InternalFlowActionExecutor implements FlowActionExecutor {

    /**
     * 返回内部方法执行器类型。
     *
     * @return 内部方法类型
     */
    @Override
    public FlowActionExecutorType type() {
        return FlowActionExecutorType.INTERNAL_METHOD;
    }

    /**
     * 调用业务服务显式提供的函数，不解析类名或反射方法。
     *
     * @param context 动作上下文
     * @return 已完成的内部动作结果
     */
    @Override
    public FlowActionResult execute(FlowActionContext context) {
        if (context.getInternalInvocation() == null) {
            throw new IllegalArgumentException("内部流程动作缺少明确业务调用");
        }
        Object output = context.getInternalInvocation().invoke();
        return FlowActionResult.builder()
            .status(FlowActionStatus.SUCCEEDED)
            .message(context.getAction().getDesc() + "已完成")
            .output(output)
            .build();
    }
}
