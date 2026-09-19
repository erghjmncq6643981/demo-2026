package com.chandler.fcc.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AdminUserEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminUserMapper;
import com.chandler.fcc.admin.model.dto.AdminUserCreateReq;
import com.chandler.fcc.admin.model.dto.AdminUserUpdateReq;
import com.chandler.fcc.admin.model.enums.AuthRoleEnum;
import com.chandler.fcc.admin.model.vo.AccountCredentialVO;
import com.chandler.fcc.admin.model.vo.AdminUserVO;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.common.util.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理控制台账号生命周期服务
 * <p>
 * fcc_admin_user 是所有控制台账号的唯一数据基准。本服务负责账号的创建、检索、
 * 启停、口令重置，并保证口令始终以 PBKDF2 派生串落库。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /**
     * 系统生成的初始口令长度
     */
    private static final int GENERATED_PASSWORD_LENGTH = 12;

    /**
     * 账号状态取值
     */
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AdminUserMapper adminUserMapper;

    /**
     * 创建控制台账号
     *
     * @param req 账号创建入参
     * @return 账号创建结果 (含一次性初始口令)
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialVO createUser(AdminUserCreateReq req) {
        String username = req.getUsername().trim();

        Long duplicated = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, username));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("登录账号已存在: " + username);
        }

        AuthRoleEnum role = resolveConsoleRole(req.getRoleCode(), AuthRoleEnum.OPERATOR);

        String plainPassword = req.getPassword();
        boolean generated = plainPassword == null || plainPassword.isBlank();
        if (generated) {
            plainPassword = PasswordHasher.generateInitialPassword(GENERATED_PASSWORD_LENGTH);
        }

        LocalDateTime now = LocalDateTime.now();
        AdminUserEntity entity = AdminUserEntity.builder()
                .id(IdUtil.nextId())
                .tenantId(0L)
                .username(username)
                .realName(req.getRealName().trim())
                .passwordHash(PasswordHasher.hash(plainPassword))
                .passwordUpdatedAt(now)
                .roleCode(role.getCode())
                .status(normalizeStatus(req.getStatus()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        adminUserMapper.insert(entity);
        log.info("[AdminUserService] 已创建控制台账号: id={}, username={}, role={}",
                entity.getId(), entity.getUsername(), entity.getRoleCode());

        return AccountCredentialVO.builder()
                .subjectType("CONSOLE")
                .id(entity.getId())
                .account(entity.getUsername())
                .displayName(entity.getRealName())
                .initialPassword(generated ? plainPassword : null)
                .hint(generated ? "请立即将初始口令交付本人并提醒其登录后修改" : "口令已按指定值设置")
                .build();
    }

    /**
     * 更新控制台账号资料与状态
     *
     * @param req 账号更新入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(AdminUserUpdateReq req) {
        AdminUserEntity existing = requireUser(req.getId());

        if (req.getRealName() != null && !req.getRealName().isBlank()) {
            existing.setRealName(req.getRealName().trim());
        }
        if (req.getRoleCode() != null && !req.getRoleCode().isBlank()) {
            existing.setRoleCode(resolveConsoleRole(req.getRoleCode(), AuthRoleEnum.OPERATOR).getCode());
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            existing.setStatus(normalizeStatus(req.getStatus()));
        }
        existing.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(existing);
        log.info("[AdminUserService] 已更新控制台账号: id={}, role={}, status={}",
                existing.getId(), existing.getRoleCode(), existing.getStatus());
    }

    /**
     * 逻辑删除控制台账号
     *
     * @param id 账号主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        AdminUserEntity existing = requireUser(id);
        existing.setDeletedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(existing);
        log.info("[AdminUserService] 已逻辑删除控制台账号: id={}, username={}", id, existing.getUsername());
    }

    /**
     * 重置指定账号口令
     *
     * @param id         账号主键 ID
     * @param rawPassword 新口令；为空则生成一次性随机口令
     * @return 口令重置结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AccountCredentialVO resetPassword(Long id, String rawPassword) {
        AdminUserEntity existing = requireUser(id);
        boolean generated = rawPassword == null || rawPassword.isBlank();
        String plainPassword = generated
                ? PasswordHasher.generateInitialPassword(GENERATED_PASSWORD_LENGTH)
                : rawPassword;

        LocalDateTime now = LocalDateTime.now();
        existing.setPasswordHash(PasswordHasher.hash(plainPassword));
        existing.setPasswordUpdatedAt(now);
        existing.setUpdatedAt(now);
        adminUserMapper.updateById(existing);
        log.info("[AdminUserService] 已重置控制台账号口令: id={}, username={}", id, existing.getUsername());

        return AccountCredentialVO.builder()
                .subjectType("CONSOLE")
                .id(existing.getId())
                .account(existing.getUsername())
                .displayName(existing.getRealName())
                .initialPassword(generated ? plainPassword : null)
                .hint("口令已重置，请通知使用人重新登录")
                .build();
    }

    /**
     * 查询全部控制台账号
     *
     * @return 账号视图列表
     */
    public List<AdminUserVO> listUsers() {
        List<AdminUserEntity> entities = adminUserMapper.selectList(new LambdaQueryWrapper<AdminUserEntity>()
                .isNull(AdminUserEntity::getDeletedAt)
                .orderByAsc(AdminUserEntity::getCreatedAt));
        return entities.stream().map(this::toVO).toList();
    }

    /**
     * 按主键查询控制台账号视图
     *
     * @param id 账号主键 ID
     * @return 账号视图
     */
    public AdminUserVO getUser(Long id) {
        return toVO(requireUser(id));
    }

    /**
     * 记录账号最近一次成功登录时间
     *
     * @param id 账号主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markLoginSuccess(Long id) {
        AdminUserEntity existing = adminUserMapper.selectById(id);
        if (existing == null) {
            return;
        }
        existing.setLastLoginAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(existing);
    }

    /**
     * 更新指定账号的口令派生串 (供认证服务统一复用)
     *
     * @param id       账号主键 ID
     * @param passwordHash 已派生口令串
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePasswordHash(Long id, String passwordHash) {
        AdminUserEntity existing = requireUser(id);
        LocalDateTime now = LocalDateTime.now();
        existing.setPasswordHash(passwordHash);
        existing.setPasswordUpdatedAt(now);
        existing.setUpdatedAt(now);
        adminUserMapper.updateById(existing);
    }

    /**
     * 按主键获取账号，不存在即抛出业务异常
     */
    private AdminUserEntity requireUser(Long id) {
        AdminUserEntity existing = adminUserMapper.selectById(id);
        if (existing == null || existing.getDeletedAt() != null) {
            throw new IllegalArgumentException("控制台账号不存在: id=" + id);
        }
        return existing;
    }

    /**
     * 校验并归一化控制台角色码，拒绝把坐席角色写入控制台账号表
     */
    private AuthRoleEnum resolveConsoleRole(String roleCode, AuthRoleEnum fallback) {
        if (roleCode == null || roleCode.isBlank()) {
            return fallback;
        }
        if (!AuthRoleEnum.isConsoleRole(roleCode)) {
            throw new IllegalArgumentException("非法控制台角色码: " + roleCode + "，仅允许 ADMIN / OPERATOR / AUDITOR");
        }
        return AuthRoleEnum.ofCode(roleCode);
    }

    /**
     * 归一化账号状态
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ENABLED;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUS_ENABLED.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new IllegalArgumentException("非法账号状态: " + status + "，仅允许 ENABLED / DISABLED");
        }
        return normalized;
    }

    /**
     * 实体转视图
     */
    private AdminUserVO toVO(AdminUserEntity entity) {
        AuthRoleEnum role = AuthRoleEnum.ofCode(entity.getRoleCode());
        return AdminUserVO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .realName(entity.getRealName())
                .roleCode(entity.getRoleCode())
                .roleName(role.getDisplayName())
                .status(entity.getStatus())
                .passwordConfigured(entity.getPasswordHash() != null && !entity.getPasswordHash().isBlank())
                .passwordUpdatedAt(entity.getPasswordUpdatedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
