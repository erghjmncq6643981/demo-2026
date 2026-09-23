package com.chandler.fcc.common.protocol;

/**
 * FNode 收号完成后的受控动作。
 */
public enum FNodeDtmfPostAction {

    PARK("收号完成或超时后驻留话道，等待后续流程"),
    HANGUP("收号完成或超时后正常挂机");

    private final String desc;

    /**
     * 创建收号后置动作。
     *
     * @param desc 中文业务说明
     */
    FNodeDtmfPostAction(String desc) {
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
