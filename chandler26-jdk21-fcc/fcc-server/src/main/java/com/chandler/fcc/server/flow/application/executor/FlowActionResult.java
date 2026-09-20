package com.chandler.fcc.server.flow.application.executor;

import lombok.Builder;
import lombok.Getter;

/**
 * 流程动作执行器返回的统一即时结果。
 */
@Getter
@Builder
public class FlowActionResult {

    private final FlowActionStatus status;
    private final String commandId;
    private final String code;
    private final String message;
    private final Object output;
}
