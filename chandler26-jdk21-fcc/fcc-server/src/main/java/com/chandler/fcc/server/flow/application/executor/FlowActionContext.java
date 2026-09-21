package com.chandler.fcc.server.flow.application.executor;

import com.chandler.fcc.common.enums.FlowActionType;
import lombok.Builder;
import lombok.Getter;

/**
 * 一次固定流程动作调用的受控上下文。
 */
@Getter
@Builder
public class FlowActionContext {

    private final FlowActionType action;
    private final String callId;
    private final String flowInstanceId;
    private final String commandId;
    private final Object payload;
    private final String endpointKey;
    private final InternalFlowActionInvocation internalInvocation;
}
