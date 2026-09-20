package com.chandler.fcc.admin.starter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AdminUserEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminUserMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentEndpointBindingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.model.enums.AuthRoleEnum;
import com.chandler.fcc.admin.service.AuthService;
import org.junit.jupiter.api.Test;

/**
 * 验证控制台会话会按数据库最新账号状态与权限重新核验。
 */
class AdminSessionRevalidationTest {

    /**
     * 已停用或删除的控制台账号不能继续使用登录时遗留的会话。
     */
    @Test
    void disabledConsoleAccountIsLoggedOut() {
        AdminUserMapper users = mock(AdminUserMapper.class);
        SaSession session = mock(SaSession.class);
        AuthService service = service(users);
        when(users.selectOne(any(Wrapper.class))).thenReturn(null);
        when(session.getString("accountType")).thenReturn(AuthService.SUBJECT_CONSOLE);

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("disabled-admin");

            assertThrows(IllegalArgumentException.class, service::getLoginUserInfo);
            stp.verify(StpUtil::logout);
        }
    }

    /**
     * 有效控制台账号的角色和权限以数据库当前值刷新到会话。
     */
    @Test
    void consolePermissionsAreRefreshedFromCurrentRole() {
        AdminUserMapper users = mock(AdminUserMapper.class);
        SaSession session = mock(SaSession.class);
        AuthService service = service(users);
        AdminUserEntity operator = AdminUserEntity.builder()
            .username("operator")
            .realName("运营管理员")
            .roleCode(AuthRoleEnum.OPERATOR.getCode())
            .status("ENABLED")
            .build();
        when(users.selectOne(any(Wrapper.class))).thenReturn(operator);
        when(session.getString("accountType")).thenReturn(AuthService.SUBJECT_CONSOLE);

        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getSession).thenReturn(session);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("operator");

            service.getLoginUserInfo();

            verify(session).set("role", AuthRoleEnum.OPERATOR.getCode());
            verify(session).set("permissions", AuthRoleEnum.OPERATOR.getPermissions());
        }
    }

    /**
     * 创建不依赖 Spring 容器的认证服务。
     *
     * @param users 控制台账号持久化端口
     * @return 认证服务
     */
    private AuthService service(AdminUserMapper users) {
        return new AuthService(
            users,
            mock(AgentMapper.class),
            mock(AgentEndpointBindingMapper.class)
        );
    }
}
