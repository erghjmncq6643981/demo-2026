package com.chandler.fcc.common.protocol;

/**
 * FNode 话道录音动作。
 */
public enum FNodeRecordAction {

    START("开始录音"),
    STOP("停止录音");

    private final String desc;

    /**
     * 创建录音动作。
     *
     * @param desc 中文业务说明
     */
    FNodeRecordAction(String desc) {
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
