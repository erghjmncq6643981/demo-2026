package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

/**
 * 坐席替班代接模式枚举
 *
 * @author Chandler
 */
@Getter
public enum SubstituteTypeEnum {

    /**
     * 点对点坐席替班
     */
    PP("PP", "点对点坐席替班"),

    /**
     * 坐席至技能组替班
     */
    PG("PG", "坐席至技能组替班");

    private final String code;
    private final String desc;

    SubstituteTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
