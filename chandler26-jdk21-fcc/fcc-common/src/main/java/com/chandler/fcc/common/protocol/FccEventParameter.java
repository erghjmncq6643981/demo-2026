package com.chandler.fcc.common.protocol;

import lombok.Getter;

/**
 * 规范事件扩展参数中的受控字段目录。
 */
@Getter
public enum FccEventParameter {

    AUTHENTICATED_EXTENSION("authenticated_extension", "已通过 SIP 认证的分机"),
    FLOW_ENTRY("flow_entry", "FreeSWITCH 拨号计划确认的业务入口"),
    SIP_USER_AGENT("sip_user_agent", "话机 User-Agent"),
    CODEC("codec", "话道读编码");

    private final String wireName;
    private final String desc;

    /**
     * 创建事件扩展参数定义。
     *
     * @param wireName JSON 字段名
     * @param desc 中文业务说明
     */
    FccEventParameter(String wireName, String desc) {
        this.wireName = wireName;
        this.desc = desc;
    }
}
