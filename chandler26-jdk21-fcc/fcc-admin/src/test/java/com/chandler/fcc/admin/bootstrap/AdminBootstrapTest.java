package com.chandler.fcc.admin.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chandler.fcc.admin.infrastructure.persistence.entity.AdminUserEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminUserMapper;
import com.chandler.fcc.admin.model.enums.AuthRoleEnum;
import com.chandler.fcc.admin.service.AdminUserService;
import com.chandler.fcc.common.util.PasswordHasher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;

/**
 * 首个管理员一次性初始化的安全边界测试。
 */
class AdminBootstrapTest {

    /**
     * 未开启 bootstrap 时不能访问账号服务。
     */
    @Test
    void disabledBootstrapDoesNothing() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        AdminUserService service = mock(AdminUserService.class);

        new AdminBootstrapRunner(properties, service).run(mock(ApplicationArguments.class));

        verifyNoInteractions(service);
    }

    /**
     * 空库首次执行会创建 ADMIN，且落库内容只包含口令哈希。
     */
    @Test
    void createsFirstAdminWithHashedPassword() {
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        AdminUserService service = new AdminUserService(mapper);

        assertTrue(service.bootstrapFirstAdmin("root_admin", "系统管理员", "StrongPass2026"));

        ArgumentCaptor<AdminUserEntity> captor = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(mapper).insert(captor.capture());
        AdminUserEntity inserted = captor.getValue();
        assertTrue(PasswordHasher.verify("StrongPass2026", inserted.getPasswordHash()));
        assertFalse(inserted.getPasswordHash().contains("StrongPass2026"));
        assertTrue(AuthRoleEnum.ADMIN.getCode().equals(inserted.getRoleCode()));
    }

    /**
     * 相同管理员已经初始化时保持幂等，绝不重置其口令。
     */
    @Test
    void existingBootstrapAdminIsNotOverwritten() {
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
            AdminUserEntity.builder()
                .username("root_admin")
                .roleCode(AuthRoleEnum.ADMIN.getCode())
                .build()
        ));
        AdminUserService service = new AdminUserService(mapper);

        assertFalse(service.bootstrapFirstAdmin("root_admin", "系统管理员", "DifferentPass2026"));

        verify(mapper, never()).insert(any(AdminUserEntity.class));
        verify(mapper, never()).updateById(any(AdminUserEntity.class));
    }

    /**
     * 已存在其他账号时拒绝把 bootstrap 当作第二条账号创建通道。
     */
    @Test
    void existingDifferentAccountRejectsBootstrap() {
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
            AdminUserEntity.builder()
                .username("existing")
                .roleCode(AuthRoleEnum.OPERATOR.getCode())
                .build()
        ));
        AdminUserService service = new AdminUserService(mapper);

        assertThrows(
            IllegalStateException.class,
            () -> service.bootstrapFirstAdmin("root_admin", "系统管理员", "StrongPass2026")
        );
        verify(mapper, never()).insert(any(AdminUserEntity.class));
    }

    /**
     * 开启初始化但缺少账号或安全口令时必须使启动失败。
     */
    @Test
    void invalidBootstrapConfigurationFailsFast() {
        AdminUserService service = new AdminUserService(mock(AdminUserMapper.class));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.bootstrapFirstAdmin("", "系统管理员", "short")
        );
    }
}
