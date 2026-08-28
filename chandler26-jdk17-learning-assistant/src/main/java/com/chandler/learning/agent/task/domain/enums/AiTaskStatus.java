package com.chandler.learning.agent.task.domain.enums;

import java.util.Set;

/** AI 业务任务生命周期状态。 */
public enum AiTaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    RETRY_WAIT("retry_wait"),
    COMPLETED("completed"),
    PARTIAL_FAILED("partial_failed"),
    ATTENTION_REQUIRED("attention_required"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private static final Set<String> TERMINAL = Set.of(
            COMPLETED.code, PARTIAL_FAILED.code, ATTENTION_REQUIRED.code, FAILED.code, CANCELLED.code);

    private final String code;

    AiTaskStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static boolean terminal(String status) {
        return TERMINAL.contains(status);
    }
}
