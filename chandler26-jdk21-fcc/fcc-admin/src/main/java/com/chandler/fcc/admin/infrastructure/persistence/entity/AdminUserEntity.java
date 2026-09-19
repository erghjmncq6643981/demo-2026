package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理控制台账号持久化实体 (fcc_admin_user)
 * <p>
 * 对应 MySQL 中 fcc_admin_user 表，是系统超管与运营人员账号的唯一数据基准。
 * 口令以 PBKDF2 派生串存储于 {@link #passwordHash}，不保存明文、不做可逆加密。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_admin_user")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "passwordHash")
@Builder
public class AdminUserEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 登录账号 (全局唯一)
     */
    private String username;

    /**
     * 账号显示姓名
     */
    private String realName;

    /**
     * 口令派生串 (pbkdf2-sha256$迭代次数$盐Hex$哈希Hex)
     */
    private String passwordHash;

    /**
     * 口令最近一次设置时间
     */
    private LocalDateTime passwordUpdatedAt;

    /**
     * 控制台角色码 (ADMIN / OPERATOR / AUDITOR)
     */
    private String roleCode;

    /**
     * 账号状态 (ENABLED / DISABLED)
     */
    private String status;

    /**
     * 最近一次成功登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
