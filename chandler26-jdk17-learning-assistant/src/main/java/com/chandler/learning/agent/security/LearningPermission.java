package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.identity.domain.enums.UserRole;

/**
 * 系统权限定义。
 *
 * <p>权限是接口声明使用的稳定业务标识，角色到权限的映射集中在这里，
 * 控制器不再直接判断角色编码。</p>
 */
public enum LearningPermission {

    /** 系统管理权限，当前由启用的系统管理员角色拥有。 */
    SYSTEM_ADMIN("system:admin");

    private final String code;

    LearningPermission(String code) {
        this.code = code;
    }

    /** 返回权限编码，供日志、审计和后续策略配置使用。 */
    public String getCode() {
        return code;
    }

    /** 判断用户是否拥有该权限。 */
    public boolean grantedTo(LearningUser user) {
        return user != null
                && Boolean.TRUE.equals(user.getEnabled())
                && this == SYSTEM_ADMIN
                && UserRole.of(user.getRoleCode()) == UserRole.ADMIN;
    }
}
