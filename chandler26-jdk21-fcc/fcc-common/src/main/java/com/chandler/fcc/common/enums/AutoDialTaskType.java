package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 自动外呼任务的业务类型。
 */
@Getter
public enum AutoDialTaskType {

    NOTIFY("通知类型"),
    SURVEY("问卷类型");

    private final String desc;

    /**
     * 创建任务类型。
     *
     * @param desc 中文业务说明
     */
    AutoDialTaskType(String desc) {
        this.desc = desc;
    }
}
