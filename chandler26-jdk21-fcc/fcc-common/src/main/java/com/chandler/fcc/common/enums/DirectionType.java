package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 呼叫方向枚举
 * <p>
 * 标识通话的业务方向（呼入、呼出或内部互拨）。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum DirectionType {

    /**
     * 呼入电话
     */
    INBOUND("呼入"),

    /**
     * 呼出电话
     */
    OUTBOUND("呼出"),

    /**
     * 内部互拨
     */
    INTERNAL("内部互拨");

    /**
     * 呼叫方向中文描述
     */
    private final String desc;

    /**
     * 构造呼叫方向枚举
     *
     * @param desc 呼叫方向中文描述
     */
    DirectionType(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串（忽略大小写）解析呼叫方向
     *
     * @param direction 方向字符串（如 "inbound", "OUTBOUND"）
     * @return 匹配的 DirectionType，默认为 INBOUND
     */
    public static DirectionType from(String direction) {
        if (direction == null || direction.trim().isEmpty()) {
            return INBOUND;
        }
        for (DirectionType type : DirectionType.values()) {
            if (type.name().equalsIgnoreCase(direction)) {
                return type;
            }
        }
        return INBOUND;
    }
}
