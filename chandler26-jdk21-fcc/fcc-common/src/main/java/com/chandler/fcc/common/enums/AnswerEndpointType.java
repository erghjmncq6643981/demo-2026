package com.chandler.fcc.common.enums;

import java.util.Locale;

/**
 * 坐席接听终端类型。
 *
 * <p>该枚举是管理面、运行面和客户端之间唯一允许使用的接听方式集合。</p>
 */
public enum AnswerEndpointType {

    /** WebRTC 软电话。 */
    WEBRTC("WebRTC 软电话", true),

    /** 已通过拨号流程绑定的物理 SIP 话机。 */
    SIP("物理 SIP 话机", true),

    /** 预留的个人手机接听方式。 */
    MOBILE("个人手机", false);

    private final String desc;
    private final boolean runtimeSupported;

    AnswerEndpointType(String desc, boolean runtimeSupported) {
        this.desc = desc;
        this.runtimeSupported = runtimeSupported;
    }

    /**
     * 返回中文业务描述。
     *
     * @return 中文描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 返回当前版本是否具备真实呼叫能力。
     *
     * @return 已实现真实路由时返回 {@code true}
     */
    public boolean isRuntimeSupported() {
        return runtimeSupported;
    }

    /**
     * 解析接口或数据库中的规范类型值。
     *
     * @param value 类型值
     * @return 接听终端类型
     * @throws IllegalArgumentException 类型为空或不受支持
     */
    public static AnswerEndpointType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("接听终端类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的接听终端类型: " + value, exception);
        }
    }
}
