package com.chandler.fcc.common.protocol;

import java.util.Arrays;
import lombok.Getter;

/**
 * Sidecar 归一化后的话道生命周期状态。
 */
@Getter
public enum ChannelEventState {

    START("START", "呼入话道已创建", false),
    CALLING("CALLING", "呼出话道已创建", false),
    RINGING("RINGING", "话道正在振铃", false),
    MEDIA("MEDIA", "话道已建立早期媒体", false),
    ANSWERED("ANSWERED", "话道已应答", false),
    READY("READY", "话道已驻留并可控制", false),
    HOLD("HOLD", "话道已保持", false),
    UNHOLD("UNHOLD", "话道已取消保持", false),
    BRIDGE("BRIDGE", "话道已进入桥接", false),
    UNBRIDGE("UNBRIDGE", "话道已退出桥接", false),
    DESTROY("DESTROY", "话道已结束", true);

    private final String wireValue;
    private final String desc;
    private final boolean terminal;

    /**
     * 创建通道事件状态。
     *
     * @param wireValue 协议状态值
     * @param desc 中文业务说明
     * @param terminal 是否为终态
     */
    ChannelEventState(String wireValue, String desc, boolean terminal) {
        this.wireValue = wireValue;
        this.desc = desc;
        this.terminal = terminal;
    }

    /**
     * 严格解析 Sidecar 上报状态。
     *
     * @param wireValue 协议状态值
     * @return 对应状态枚举
     * @throws IllegalArgumentException 状态未知
     */
    public static ChannelEventState fromWireValue(String wireValue) {
        return Arrays.stream(values())
            .filter(state -> state.wireValue.equals(wireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的话道状态: " + wireValue));
    }
}
