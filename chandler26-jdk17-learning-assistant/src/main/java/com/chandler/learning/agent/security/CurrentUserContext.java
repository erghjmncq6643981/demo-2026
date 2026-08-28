package com.chandler.learning.agent.security;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 当前请求线程的已认证用户上下文。
 * <p>
 * JWT 只由 {@link JwtAuthenticationFilter} 解析一次。本组件仅从 Spring Security 上下文读取过滤器已确认的用户，
 * 供 Controller、应用服务和审计基础设施复用。
 */
@Component
public class CurrentUserContext {

    /** 获取当前用户；受保护接口未认证时返回标准未登录异常。 */
    public LearningUser requireUser() {
        return findUser().orElseThrow(() -> LearningAssistantException.unauthorized(
                LearningErrorCode.AUTH_REQUIRED));
    }

    /** 获取当前系统管理员；普通用户访问管理资源时返回统一权限错误。 */
    public LearningUser requireAdmin() {
        return requirePermissions(LearningPermission.SYSTEM_ADMIN);
    }

    /**
     * 校验当前用户是否同时具备注解声明的权限。
     * <p>保留 {@link #requireAdmin()} 作为应用服务等非接口场景的兼容入口，
     * Controller 权限应优先使用 {@link RequirePermission} 声明。</p>
     */
    public LearningUser requirePermissions(LearningPermission... permissions) {
        LearningUser user = requireUser();
        if (permissions == null || permissions.length == 0) {
            return user;
        }
        for (LearningPermission permission : permissions) {
            if (!hasPermission(user, permission)) {
                throw LearningAssistantException.of(LearningErrorCode.ADMIN_REQUIRED);
            }
        }
        return user;
    }

    /** 判断已认证用户是否拥有权限，适用于同一接口内存在个人与管理员数据范围的场景。 */
    public boolean hasPermission(LearningUser user, LearningPermission permission) {
        return permission != null && permission.grantedTo(user);
    }

    /** 在不要求登录的基础设施场景中安全读取当前用户。 */
    public Optional<LearningUser> findUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LearningUserPrincipal userPrincipal && userPrincipal.user() != null) {
            return Optional.of(userPrincipal.user());
        }
        return Optional.empty();
    }
}
