package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 通话挂机发起方枚举
 * <p>
 * 记录是主叫客户、坐席、系统还是管理员触发了通话挂断操作。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum HangupInitiator {

    /**
     * 主叫方先挂断
     */
    CALLER("主叫先挂"),

    /**
     * 被叫方先挂断
     */
    CALLEE("被叫先挂"),

    /**
     * 系统逻辑触发挂断（如超时、异常、黑名单拦截等）
     */
    SYSTEM("系统拆线"),

    /**
     * 管理员在管理台或监控大厅执行强拆
     */
    ADMIN("管理员强拆");

    /**
     * 挂机发起方中文描述
     */
    private final String desc;

    /**
     * 构造挂机发起方枚举
     *
     * @param desc 中文描述
     */
    HangupInitiator(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串解析挂机发起方
     *
     * @param name 字符串标识
     * @return 匹配的 HangupInitiator，未匹配返回 SYSTEM
     */
    public static HangupInitiator from(String name) {
        if (name == null || name.trim().isEmpty()) {
            return SYSTEM;
        }
        for (HangupInitiator initiator : HangupInitiator.values()) {
            if (initiator.name().equalsIgnoreCase(name)) {
                return initiator;
            }
        }
        return SYSTEM;
    }
}
