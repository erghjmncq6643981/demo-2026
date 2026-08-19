package com.chandler.learning.agent.domain.enums;

import java.util.Arrays;

/**
 * 系统用户角色。
 */
public enum UserRole {

    USER("USER", "普通用户"),
    ADMIN("ADMIN", "系统管理员");

    private final String code;
    private final String label;

    UserRole(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 将存量空角色按普通用户处理，保证升级后历史账号可继续登录。 */
    public static UserRole of(String code) {
        if (code == null || code.isBlank()) {
            return USER;
        }
        return Arrays.stream(values())
                .filter(role -> role.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(USER);
    }
}
