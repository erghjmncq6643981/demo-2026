package com.chandler.fcc.common.protocol;

import lombok.Getter;

/**
 * FCC 规范事件载荷中的公共字段目录。
 */
@Getter
public enum FccEventField {

    EVENT_ID("event_id", "事件唯一标识"),
    NODE_ID("node_id", "事件来源节点"),
    COMMAND_ID("command_id", "指令唯一标识"),
    COMMAND_METHOD("command_method", "指令方法名"),
    COMMAND_STATUS("command_status", "指令最终状态"),
    COMMAND_CODE("code", "指令结果代码"),
    COMMAND_MESSAGE("message", "指令结果说明"),
    CONTROL_ID("ctrl_uuid", "控制关联标识"),
    CHANNEL_UUID("uuid", "话道唯一标识"),
    PEER_CHANNEL_UUID("peer_uuid", "对端话道唯一标识"),
    STATE("state", "话道状态"),
    DIRECTION("direction", "话道方向"),
    CALLER_NUMBER("cid_number", "主叫号码"),
    DESTINATION_NUMBER("dest_number", "被叫号码"),
    CONTEXT("context", "FreeSWITCH 拨号计划上下文"),
    CAUSE("cause", "挂机原因"),
    DURATION("duration", "通话持续秒数"),
    BILL_SECONDS("billsec", "计费秒数"),
    DIGIT("digit", "DTMF 按键"),
    DTMF_SOURCE("source", "DTMF 事件来源"),
    RESULT("result", "指令结构化结果"),
    SOURCE_TIMESTAMP("timestamp", "事件源时间戳"),
    PARAMETERS("params", "扩展规范参数");

    private final String wireName;
    private final String desc;

    /**
     * 创建事件字段定义。
     *
     * @param wireName JSON 字段名
     * @param desc 中文业务说明
     */
    FccEventField(String wireName, String desc) {
        this.wireName = wireName;
        this.desc = desc;
    }
}
