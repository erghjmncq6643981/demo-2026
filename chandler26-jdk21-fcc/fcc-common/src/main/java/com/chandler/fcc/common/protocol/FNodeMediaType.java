package com.chandler.fcc.common.protocol;

/**
 * FNode 媒体载荷类型。
 */
public enum FNodeMediaType {

    TEXT("待由 Sidecar 合成为共享音频的文案"),
    FILE("FreeSWITCH 可读取的共享音频文件");

    private final String desc;

    /**
     * 创建媒体载荷类型。
     *
     * @param desc 中文业务说明
     */
    FNodeMediaType(String desc) {
        this.desc = desc;
    }

    /**
     * 返回中文业务说明。
     *
     * @return 中文业务说明
     */
    public String getDesc() {
        return desc;
    }
}
