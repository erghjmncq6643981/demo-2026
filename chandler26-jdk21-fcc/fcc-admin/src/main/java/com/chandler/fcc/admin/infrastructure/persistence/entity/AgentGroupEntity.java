package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席技能组持久化实体 (fcc_agent_group)
 * <p>
 * 对应 MySQL 中 fcc_agent_group 表，管理技能组代码、路由策略与启用状态。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_agent_group")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AgentGroupEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 技能组雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 父技能组 ID (支持树形分组)
     */
    private Long parentId;

    /**
     * 技能组编码 (唯一标识)
     */
    private String groupCode;

    /**
     * 技能组名称
     */
    private String groupName;

    /**
     * 组类型 (SKILL, BUSINESS, VIRTUAL)
     */
    private String groupType;

    /**
     * 话务路由分发策略 (LONGEST_IDLE, ROUND_ROBIN, PRIORITY, RANDOM)
     */
    private String routingStrategy;

    /**
     * 状态 (ENABLED, DISABLED)
     */
    private String status;

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
