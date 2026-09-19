package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * SIP 分机与坐席终端注册态枚举
 * <p>
 * 标识 WebRTC 网页耳麦或 SIP 物理话机在 Sofia SIP 引擎上的实时注册状态。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum PresenceStatus {

    /**
     * 终端已成功注册在线
     */
    REGISTERED("已注册在线"),

    /**
     * 终端已主动发起注销下线
     */
    UNREGISTERED("已主动注销"),

    /**
     * 终端心跳或注册租约超时离线
     */
    EXPIRED("超时离线");

    /**
     * 注册态中文描述
     */
    private final String desc;

    /**
     * 构造注册态枚举
     *
     * @param desc 中文描述
     */
    PresenceStatus(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串解析注册态
     *
     * @param status 状态字符串
     * @return 匹配的 PresenceStatus，未匹配时返回 UNREGISTERED
     */
    public static PresenceStatus from(String status) {
        if (status == null || status.trim().isEmpty()) {
            return UNREGISTERED;
        }
        for (PresenceStatus s : PresenceStatus.values()) {
            if (s.name().equalsIgnoreCase(status)) {
                return s;
            }
        }
        return UNREGISTERED;
    }

    /**
     * 判断终端是否处于可用在线状态
     *
     * @return 若在线返回 true，否则返回 false
     */
    public boolean isOnline() {
        return this == REGISTERED;
    }
}
