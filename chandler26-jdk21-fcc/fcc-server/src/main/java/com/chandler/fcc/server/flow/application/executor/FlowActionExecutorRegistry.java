package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.enums.FlowActionExecutorType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 流程动作执行器唯一注册中心。
 *
 * <p>启动时拒绝执行器缺失或重复注册，执行时同时校验动作目录与执行器类型，
 * 防止模型通过 operation 绕过公共目录。</p>
 */
@Component
public class FlowActionExecutorRegistry {

    private final Map<FlowActionExecutorType, FlowActionExecutor> executors;

    /**
     * 创建并校验执行器注册中心。
     *
     * @param candidates Spring 容器中的全部执行器
     */
    public FlowActionExecutorRegistry(List<FlowActionExecutor> candidates) {
        EnumMap<FlowActionExecutorType, FlowActionExecutor> registered = new EnumMap<>(
            FlowActionExecutorType.class
        );
        for (FlowActionExecutor candidate : candidates) {
            FlowActionExecutor previous = registered.putIfAbsent(candidate.type(), candidate);
            if (previous != null) {
                throw new IllegalStateException("流程执行器重复注册: " + candidate.type());
            }
        }
        for (FlowActionExecutorType type : FlowActionExecutorType.values()) {
            if (!registered.containsKey(type)) {
                throw new IllegalStateException("流程执行器未注册: " + type);
            }
        }
        this.executors = Map.copyOf(registered);
    }

    /**
     * 通过公共动作目录选择唯一执行器。
     *
     * @param context 动作上下文
     * @return 即时执行结果
     */
    public FlowActionResult execute(FlowActionContext context) {
        if (context == null || context.getAction() == null) {
            throw new IllegalArgumentException("流程动作不能为空");
        }
        return executors.get(context.getAction().getExecutorType()).execute(context);
    }
}
