package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * WebSocket 实时推送消息类型枚举
 *
 * @author Chandler
 * @version 1.0.0
 * @since 2026-09-18
 */
@Getter
public enum WsMessageTypeEnum {

    /**
     * 话务弹屏事件
     * <p>
     * 呼入时即"来电弹屏"，呼出时即"外呼弹屏"，两者共用同一类型，
     * 由载荷中的 {@code direction} 区分方向。
     * </p>
     */
    CALL_SCREEN_POP("SCREEN_POP", "话务弹屏"),

    /**
     * 呼叫接通事件
     */
    CALL_ANSWERED("CALL_ANSWERED", "通话已接通"),

    /**
     * 呼叫挂机事件
     */
    CALL_HANGUP("CALL_HANGUP", "通话已挂断"),

    /**
     * 坐席通道就绪 (握手确认)
     */
    CHANNEL_READY("CHANNEL_READY", "坐席通道已就绪"),

    /**
     * 坐席状态变更通知
     */
    AGENT_PRESENCE_CHANGE("AGENT_PRESENCE_CHANGE", "坐席态势变更通知"),

    /**
     * 客户端话务控制动作
     */
    CALL_CONTROL_ACTION("CALL_CONTROL_ACTION", "客户端话务控制动作"),

    /**
     * 心跳 Ping
     */
    HEARTBEAT_PING("HEARTBEAT_PING", "心跳探活请求"),

    /**
     * 心跳 Pong
     */
    HEARTBEAT_PONG("HEARTBEAT_PONG", "心跳探活响应");

    private final String code;
    private final String desc;

    WsMessageTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
