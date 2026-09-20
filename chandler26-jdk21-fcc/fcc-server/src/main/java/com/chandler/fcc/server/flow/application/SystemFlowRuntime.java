package com.chandler.fcc.server.flow.application;

import com.chandler.fcc.common.enums.FlowActionType;
import java.util.Set;

/**
 * 固定系统流程的运行端能力契约。
 *
 * <p>各业务应用服务通过该契约声明自己实际执行的模板和动作。声明只用于启动期完整性校验，
 * 事件处理仍由各业务服务按通话事实驱动，不提供任意动作反射执行入口。</p>
 */
public interface SystemFlowRuntime {

    /**
     * 返回当前运行服务负责的固定模板。
     *
     * @return 不可变模板代码集合
     */
    Set<String> templates();

    /**
     * 返回指定模板由当前运行服务实际执行的动作。
     *
     * @param template 固定模板代码
     * @return 不可变动作集合；不负责该模板时返回空集合
     */
    Set<FlowActionType> supportedActions(String template);
}
