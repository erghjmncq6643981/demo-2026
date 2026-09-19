package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 话务流转模式枚举
 * <p>
 * 定义 FCC 核心流程编排引擎所支持的标准话务流转模式。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum FlowModelType {

    /**
     * 客户呼入客服模式：客户呼入 -> IVR 导航放音收号 -> 技能组排队 -> 坐席应答 -> 双方桥接 -> 满意度评价
     */
    INBOUND_CUSTOMER_SERVICE("客户呼入客服模式"),

    /**
     * 坐席外呼双向通话模式：坐席外呼 -> 坐席话道就绪 -> 外呼客户 -> 客户应答 -> 双方桥接
     */
    OUTBOUND_TWO_WAY_CALL("坐席外呼双向通话模式"),

    /**
     * 自动外呼通知模式：系统自动外呼客户 -> 客户接听 -> 播放通知语音 -> 按键收号确认意向
     */
    AUTO_DIAL_NOTIFICATION("自动外呼通知模式"),

    /**
     * 盲转或直接转接模式：通话中转接第三方坐席或外部号码
     */
    DIRECT_TRANSFER("盲转/直接转接模式");

    /**
     * 流程模式中文描述
     */
    private final String desc;

    /**
     * 构造流程模式枚举
     *
     * @param desc 流程模式中文描述
     */
    FlowModelType(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串（忽略大小写）解析话务流转模式
     *
     * @param modelKey 模式字符串
     * @return 匹配的 FlowModelType，若无匹配则返回 INBOUND_CUSTOMER_SERVICE
     */
    public static FlowModelType from(String modelKey) {
        if (modelKey == null || modelKey.trim().isEmpty()) {
            return INBOUND_CUSTOMER_SERVICE;
        }
        for (FlowModelType type : FlowModelType.values()) {
            if (type.name().equalsIgnoreCase(modelKey)) {
                return type;
            }
        }
        return INBOUND_CUSTOMER_SERVICE;
    }
}
