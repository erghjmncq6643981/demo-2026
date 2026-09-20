package com.chandler.fcc.server.flow.application.executor;

/**
 * 由业务应用服务提供的明确内部动作调用，不使用反射或模型中的类名。
 */
@FunctionalInterface
public interface InternalFlowActionInvocation {

    /**
     * 执行当前业务服务拥有的内部动作。
     *
     * @return 可持久化或可序列化的动作结果
     */
    Object invoke();
}
