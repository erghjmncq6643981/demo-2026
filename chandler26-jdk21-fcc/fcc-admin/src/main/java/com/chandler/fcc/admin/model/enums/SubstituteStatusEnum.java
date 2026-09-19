package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

/**
 * 坐席替班审批状态枚举
 *
 * @author Chandler
 */
@Getter
public enum SubstituteStatusEnum {

    /**
     * 待确认/待生效
     */
    UNCONFIRM("UNCONFIRM", "待确认"),

    /**
     * 已确认生效
     */
    CONFIRMED("CONFIRMED", "已确认生效"),

    /**
     * 已撤回/已取消
     */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    SubstituteStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
