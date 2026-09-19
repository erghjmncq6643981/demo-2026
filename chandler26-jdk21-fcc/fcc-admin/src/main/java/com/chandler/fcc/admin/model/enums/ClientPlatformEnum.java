package com.chandler.fcc.admin.model.enums;

import lombok.Getter;

/**
 * 客户端运行平台操作系统枚举
 *
 * @author Chandler
 */
@Getter
public enum ClientPlatformEnum {

    /**
     * Windows 操作系统
     */
    WINDOWS("WINDOWS", "Windows操作系统"),

    /**
     * macOS 操作系统
     */
    MAC("MAC", "macOS操作系统"),

    /**
     * Linux 操作系统
     */
    LINUX("LINUX", "Linux操作系统"),

    /**
     * Web 浏览器端
     */
    WEB("WEB", "Web浏览器端");

    private final String code;
    private final String desc;

    ClientPlatformEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
