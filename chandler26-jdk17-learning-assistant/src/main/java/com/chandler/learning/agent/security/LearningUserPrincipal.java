package com.chandler.learning.agent.security;

import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * LearningUserPrincipal 类。
 */
public class LearningUserPrincipal implements UserDetails {

    private final LearningUser user;

    /**
     * 处理 {@code LearningUserPrincipal} 相关业务。
     */
    public LearningUserPrincipal(LearningUser user) {
        this.user = user;
    }

    /**
     * 处理 {@code user} 相关业务。
     */
    public LearningUser user() {
        return user;
    }

    /**
     * 查询 {@code getAuthorities} 相关业务。
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleCode = user.getRoleCode() == null || user.getRoleCode().isBlank()
                ? "USER" : user.getRoleCode().trim().toUpperCase();
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleCode));
    }

    /**
     * 查询 {@code getPassword} 相关业务。
     */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /**
     * 查询 {@code getUsername} 相关业务。
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * 判断 {@code isEnabled} 相关业务。
     */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
