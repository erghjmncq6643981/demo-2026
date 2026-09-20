package com.chandler.fcc.server.flow.application;

import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.SystemFlowModels;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 在服务启动完成前校验固定模型动作与运行端能力完全一致。
 */
@Component
@RequiredArgsConstructor
public class SystemFlowActionCoverageValidator implements SmartInitializingSingleton {

    private final List<SystemFlowRuntime> runtimes;

    /**
     * 校验所有公共模型动作都有且只有明确的运行端实现声明。
     *
     * @throws IllegalStateException 模型动作缺少实现或运行端声明了模型之外的动作
     */
    @Override
    public void afterSingletonsInstantiated() {
        for (String template : SystemFlowModels.templateNames()) {
            Set<FlowActionType> expected = SystemFlowModels.actions(template);
            EnumSet<FlowActionType> actual = EnumSet.noneOf(FlowActionType.class);
            for (SystemFlowRuntime runtime : runtimes) {
                if (runtime.templates().contains(template)) {
                    actual.addAll(runtime.supportedActions(template));
                }
            }

            EnumSet<FlowActionType> missing = EnumSet.copyOf(expected);
            missing.removeAll(actual);
            EnumSet<FlowActionType> unexpected = EnumSet.copyOf(actual);
            unexpected.removeAll(expected);
            if (!missing.isEmpty() || !unexpected.isEmpty()) {
                throw new IllegalStateException(
                    "固定流程动作覆盖不完整 template=" + template
                        + ", missing=" + missing
                        + ", unexpected=" + unexpected
                );
            }
        }
    }
}
