package com.chandler.learning.agent.security;

import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LearningUserPrincipal implements UserDetails {

    private final LearningUser user;

    public LearningUserPrincipal(LearningUser user) {
        this.user = user;
    }

    public LearningUser user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
