package com.chandler.fcc.common.enums;

import lombok.Getter;

/**
 * 坐席临时代班类型枚举
 * <p>
 * 对应 fcc_agent_substitute_record 表的代班模式。
 * </p>
 *
 * @author Chandler
 */
@Getter
public enum SubstituteType {

    /**
     * 坐席对坐席点对点代班（原坐席的话务穿透转由指定代班坐席接听）
     */
    PP("坐席对坐席"),

    /**
     * 坐席对技能组代班（原坐席的话务回流至指定技能组由组内空闲坐席接听）
     */
    PG("坐席对技能组");

    /**
     * 代班类型中文描述
     */
    private final String desc;

    /**
     * 构造代班类型枚举
     *
     * @param desc 中文描述
     */
    SubstituteType(String desc) {
        this.desc = desc;
    }

    /**
     * 根据字符串解析代班类型
     *
     * @param name 字符串标识
     * @return 匹配的 SubstituteType，默认返回 PP
     */
    public static SubstituteType from(String name) {
        if (name == null || name.trim().isEmpty()) {
            return PP;
        }
        for (SubstituteType type : SubstituteType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return PP;
    }
}
