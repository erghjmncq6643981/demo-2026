package com.chandler.fcc.common.protocol;

import java.util.Arrays;
import lombok.Getter;

/**
 * Sidecar 异步指令最终状态。
 */
@Getter
public enum FccCommandResultStatus {

    SUCCEEDED("SUCCEEDED", "指令已完成并取得成功结果"),
    FAILED("FAILED", "指令已结束但执行失败");

    private final String wireValue;
    private final String desc;

    /**
     * 创建指令最终状态。
     *
     * @param wireValue 协议值
     * @param desc 中文业务说明
     */
    FccCommandResultStatus(String wireValue, String desc) {
        this.wireValue = wireValue;
        this.desc = desc;
    }

    /**
     * 严格解析协议最终状态。
     *
     * @param wireValue 协议值
     * @return 指令最终状态
     * @throws IllegalArgumentException 状态不在规范目录中
     */
    public static FccCommandResultStatus fromWireValue(String wireValue) {
        return Arrays.stream(values())
            .filter(status -> status.wireValue.equals(wireValue))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的指令最终状态: " + wireValue));
    }
}
