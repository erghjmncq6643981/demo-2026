package com.chandler.fcc.common.protocol;

import lombok.Getter;

/**
 * FreeSWITCH 拨号计划向 FCC 声明的可信业务入口。
 */
@Getter
public enum FccFlowEntry {

    PHONE_BINDING("PHONE_BINDING", "已认证 SIP 话机自助绑定入口");

    private final String wireValue;
    private final String desc;

    /**
     * 创建拨号计划业务入口。
     *
     * @param wireValue 协议值
     * @param desc 中文业务说明
     */
    FccFlowEntry(String wireValue, String desc) {
        this.wireValue = wireValue;
        this.desc = desc;
    }
}
