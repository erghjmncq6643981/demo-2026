package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席与技能组成员关联实体 (fcc_agent_group_member)
 * <p>
 * 对应 MySQL 中 fcc_agent_group_member 表，记录坐席在组内的成员角色与分发优先级。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_agent_group_member")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AgentGroupMemberEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 技能组 ID
     */
    private Long groupId;

    /**
     * 坐席 ID
     */
    private Long agentId;

    /**
     * 组内成员角色 (LEADER-班长席, MEMBER-普通成员)
     */
    private String memberRole;

    /**
     * 话务分发优先级 (数值越小优先级越高，默认 0)
     */
    private Integer priority;

    /**
     * 加入时间
     */
    private LocalDateTime createdAt;

    /**
     * 逻辑删除标记时间
     */
    private LocalDateTime deletedAt;
}
