package com.chandler.fcc.server.flow.application.executor;

import lombok.Getter;

/**
 * 单次流程动作调用的即时结果状态。
 */
@Getter
public enum FlowActionStatus {

    ACCEPTED("请求已受理，等待业务事件确认"),
    SUCCEEDED("内部业务动作已完成"),
    FAILED("动作已明确失败"),
    UNKNOWN("动作结果未知，需要查询或对账");

    private final String desc;

    /**
     * 创建动作状态。
     *
     * @param desc 中文业务说明
     */
    FlowActionStatus(String desc) {
        this.desc = desc;
    }
}
