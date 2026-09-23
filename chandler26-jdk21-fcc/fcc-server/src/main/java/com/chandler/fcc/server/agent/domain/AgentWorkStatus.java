package com.chandler.fcc.server.agent.domain;

import lombok.Getter;

/**
 * 用于快速路由和监控的坐席有效工作状态。
 */
@Getter
public enum AgentWorkStatus {

    READY("示闲，当前可接来电或主动外呼"),
    UNREADY("已退出，不参与呼叫"),
    BUSY("示忙，不接来电但允许主动外呼"),
    CALLING("正在发起呼叫"),
    RINGING("坐席终端正在振铃"),
    ANSWERED("坐席已应答或正在通话"),
    ACW("话后整理");

    private final String desc;

    AgentWorkStatus(String desc) {
        this.desc = desc;
    }

    /**
     * 解析持久化代码。
     *
     * @param code 状态代码
     * @return 工作状态
     */
    public static AgentWorkStatus fromCode(String code) {
        for (AgentWorkStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知坐席工作状态: " + code);
    }
}
