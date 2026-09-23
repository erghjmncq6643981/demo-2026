package com.chandler.fcc.common.protocol;

/**
 * FNode 放音完成后的受控动作。
 */
public enum FNodePlayPostAction {

    NONE("播放完成后继续当前话务"),
    HANGUP("播放完成后正常挂机");

    private final String desc;

    /**
     * 创建放音后置动作。
     *
     * @param desc 中文业务说明
     */
    FNodePlayPostAction(String desc) {
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
