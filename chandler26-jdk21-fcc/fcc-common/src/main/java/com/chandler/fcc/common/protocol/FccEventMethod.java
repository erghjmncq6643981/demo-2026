package com.chandler.fcc.common.protocol;

import java.util.Arrays;
import lombok.Getter;

/**
 * Sidecar 向 Java 控制面发布的规范事件方法目录。
 */
@Getter
public enum FccEventMethod {

    CHANNEL("Event.Channel", "channel", "话道生命周期事件"),
    COMMAND_RESULT("Event.CommandResult", "command", "异步指令最终结果事件"),
    DTMF("Event.DTMF", "dtmf", "话道按键事件"),
    RECORDING("Event.Recording", "record", "录音生命周期事件"),
    REGISTRATION("Event.Registration", "registration", "SIP 分机注册事件"),
    GATEWAY("Event.Gateway", "gateway", "SIP 网关状态事件"),
    SUPERVISION("Event.Supervision", "supervision", "监听、耳语和强插事件");

    private final String wireName;
    private final String category;
    private final String desc;

    /**
     * 创建规范事件方法。
     *
     * @param wireName JSON-RPC 通知方法名
     * @param category NATS 事件主题分类
     * @param desc 中文业务说明
     */
    FccEventMethod(String wireName, String category, String desc) {
        this.wireName = wireName;
        this.category = category;
        this.desc = desc;
    }

    /**
     * 按 wire 方法名严格解析事件。
     *
     * @param wireName JSON-RPC 通知方法名
     * @return 对应事件方法
     * @throws IllegalArgumentException 方法不在规范目录中
     */
    public static FccEventMethod fromWireName(String wireName) {
        return Arrays.stream(values())
            .filter(method -> method.wireName.equals(wireName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的事件方法: " + wireName));
    }
}
