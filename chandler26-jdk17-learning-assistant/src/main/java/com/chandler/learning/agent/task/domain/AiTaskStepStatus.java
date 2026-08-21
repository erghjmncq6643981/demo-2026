package com.chandler.learning.agent.task.domain;

/** AI 任务步骤状态。 */
public enum AiTaskStepStatus {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    SKIPPED("skipped"),
    CANCELLED("cancelled");

    private final String code;

    AiTaskStepStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
