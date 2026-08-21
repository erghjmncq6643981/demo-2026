package com.chandler.learning.agent.task.domain;

/** AI 任务触发来源。 */
public enum AiTaskTriggerType {
    USER("user"),
    ADMIN("admin"),
    SYSTEM("system");

    private final String code;

    AiTaskTriggerType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
