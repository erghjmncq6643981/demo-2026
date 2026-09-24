package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 自动外呼任务的业务类型。
 */
@Getter
public enum AutoDialTaskType {

    NOTIFY("语音通知");

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
