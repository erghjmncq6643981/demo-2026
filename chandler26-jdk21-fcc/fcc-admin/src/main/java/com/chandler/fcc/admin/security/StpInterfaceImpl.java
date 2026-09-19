package com.chandler.fcc.admin.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 自定义权限与角色加载接口实现类
 * <p>
 * 从当前会话 SaSession (由 Redis 集中托管) 中读取角色与权限列表。
 * </p>
 *
 * @author Chandler
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return Collections.emptyList();
        }
        Object perms = session.get("permissions");
        if (perms instanceof List<?>) {
            return (List<String>) perms;
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId, false);
        if (session == null) {
            return Collections.emptyList();
        }
        Object roles = session.get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }
}
