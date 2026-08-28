package com.chandler.learning.agent.system.domain.enums;

/** 系统日志 Outbox 的处理状态。 */
public enum SystemLogOutboxStatus {

    PENDING("pending"),
    PROCESSING("processing"),
    SUCCEEDED("succeeded");

    private final String code;

    SystemLogOutboxStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
