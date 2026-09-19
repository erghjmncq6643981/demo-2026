package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

/**
 * 动态系统业务配置作用域枚举
 *
 * @author Chandler
 */
@Getter
public enum SystemConfigScopeEnum {

    /**
     * 前端 Web 门户配置
     */
    WEB("WEB", "前端Web门户"),

    /**
     * 后台服务核心配置
     */
    BACKEND("BACKEND", "后台服务端"),

    /**
     * 坐席 PC 客户端配置
     */
    CLIENT("CLIENT", "坐席PC客户端"),

    /**
     * 底层系统/平台级配置
     */
    SYSTEM("SYSTEM", "底层系统平台");

    private final String code;
    private final String desc;

    SystemConfigScopeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
