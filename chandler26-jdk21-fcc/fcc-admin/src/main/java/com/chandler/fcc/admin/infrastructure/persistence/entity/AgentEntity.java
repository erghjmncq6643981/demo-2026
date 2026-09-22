package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 坐席主数据持久化实体 (fcc_agent)
 * <p>
 * 对应 MySQL 中 fcc_agent 表，记录坐席基本档案、工号、登录口令、角色与启用状态。
 * 本表同时是坐席登录账号的唯一数据基准。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_agent")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(exclude = "passwordHash")
@SuperBuilder
public class AgentEntity extends BaseEntity {

    /**
     * 坐席工号 (全局唯一，同时作为登录账号)
     */
    private String workNo;

    /**
     * 坐席真实姓名
     */
    private String agentName;

    /**
     * 手机号码
     */
    private String phoneNumber;

    /**
     * 角色标识 (如 AGENT_ADMIN, SUPERVISOR, AGENT_MEMBER)
     */
    private String roleCode;

    /**
     * 状态 (ENABLED, DISABLED)
     */
    private String status;

    /**
     * 坐席登录口令派生串 (pbkdf2-sha256$迭代次数$盐Hex$哈希Hex)
     * <p>为空表示该坐席尚未开通工作台登录，认证时一律拒绝。</p>
     */
    private String passwordHash;

    /**
     * 口令最近一次设置时间
     */
    private LocalDateTime passwordUpdatedAt;

    /**
     * 最近一次成功登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 扩展元数据 (JSON 字符串)
     */
    private String metadata;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
