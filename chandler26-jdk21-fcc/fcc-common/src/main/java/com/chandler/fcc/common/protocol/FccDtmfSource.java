package com.chandler.fcc.common.protocol;

import java.util.Arrays;
import lombok.Getter;

/**
 * DTMF 事件来源。
 */
@Getter
public enum FccDtmfSource {

    KEY_PRESS("KEY_PRESS", "话机产生的单个物理按键");

    private final String wireValue;
    private final String desc;

    /**
     * 创建 DTMF 事件来源。
     *
     * @param wireValue 协议值
     * @param desc 中文业务说明
     */
    FccDtmfSource(String wireValue, String desc) {
        this.wireValue = wireValue;
        this.desc = desc;
    }

    /**
     * 按协议值严格解析来源。
     *
     * @param wireValue 协议值
     * @return DTMF 事件来源
     * @throws IllegalArgumentException 协议值未知
     */
    public static FccDtmfSource fromWireValue(String wireValue) {
        return Arrays.stream(values())
            .filter(source -> source.wireValue.equals(wireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的 DTMF 事件来源: " + wireValue));
    }
}
