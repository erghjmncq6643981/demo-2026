package com.chandler.learning.agent.security;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 用户账户领域组件。
 */
public class LearningUserPrincipal implements UserDetails {

    private final LearningUser user;

    /** 构建 Spring Security 登录用户主体。 */
    public LearningUserPrincipal(LearningUser user) {
        this.user = user;
    }

    /** 返回当前登录用户领域对象。 */
    public LearningUser user() {
        return user;
    }

    /** 返回当前用户的 Spring Security 权限集合。 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleCode = user.getRoleCode() == null || user.getRoleCode().isBlank()
                ? "USER" : user.getRoleCode().trim().toUpperCase();
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    /** Spring Security 认证不暴露密码。 */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** 返回 Spring Security 主体用户名。 */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /** 返回当前用户账户是否启用。 */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
