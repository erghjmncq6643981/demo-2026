package com.chandler.learning.agent.task.application.contract;

/** 一个任务类型固定的步骤定义。 */
public record AiTaskStepDefinition(String code, String name, int order, int totalCount) {

    public AiTaskStepDefinition(String code, String name, int order) {
        this(code, name, order, 1);
    }
}
