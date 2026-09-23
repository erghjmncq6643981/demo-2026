package com.chandler.fcc.server.agent.domain;

import lombok.Getter;

/**
 * 坐席业务登录状态。
 */
@Getter
public enum AgentLoginStatus {

    LOGIN("示闲，可接来电和主动外呼", true),
    LOGOUT("已退出，不参与呼叫", false),
    LOGIN_BUSY("示忙，可主动外呼但不接来电", true);

    private final String desc;
    private final boolean manuallySelectable;

    AgentLoginStatus(String desc, boolean manuallySelectable) {
        this.desc = desc;
        this.manuallySelectable = manuallySelectable;
    }

    /**
     * 解析持久化代码。
     *
     * @param code 状态代码
     * @return 登录状态
     */
    public static AgentLoginStatus fromCode(String code) {
        for (AgentLoginStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知坐席登录状态: " + code);
    }
}
