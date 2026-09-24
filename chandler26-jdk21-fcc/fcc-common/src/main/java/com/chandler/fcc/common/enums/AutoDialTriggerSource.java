package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 自动外呼任务的业务触发来源。
 *
 * <p>当前仅开放管理端创建；API 与 MQ 是后续接入业务系统时使用的稳定契约。</p>
 */
@Getter
public enum AutoDialTriggerSource {

    FRONTEND("管理端创建"),
    API("业务接口触发"),
    MQ("业务消息触发");

    private final String desc;

    /**
     * 创建触发来源。
     *
     * @param desc 中文业务说明
     */
    AutoDialTriggerSource(String desc) {
        this.desc = desc;
    }
}
