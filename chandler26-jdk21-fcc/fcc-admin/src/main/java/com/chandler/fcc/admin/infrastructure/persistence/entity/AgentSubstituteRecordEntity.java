package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席替班与夜班代接记录持久化实体 (fcc_agent_substitute_record)
 * <p>
 * 对应 MySQL 中 fcc_agent_substitute_record 表，记录坐席夜班、请假替班的流转状态与时间窗口。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_agent_substitute_record")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AgentSubstituteRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 替班记录雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /**
     * 申请人坐席 ID
     */
    private Long applicantAgentId;

    /**
     * 替班代接坐席 ID
     */
    private Long substituteAgentId;

    /**
     * 替班类型 (PP-点对点坐席, PG-坐席到技能组)
     */
    private String substituteType;

    /**
     * 替班业务场景 (NIGHT_OFF-夜班, TEMPORARY_LEAVE-临时事假)
     */
    private String scope;

    /**
     * 替班生效起始时间
     */
    private LocalDateTime startTime;

    /**
     * 替班生效结束时间
     */
    private LocalDateTime endTime;

    /**
     * 替班优先级 (默认 99)
     */
    private Integer priority;

    /**
     * 审批流状态 (UNCONFIRM-待确认, CONFIRMED-已生效, CANCELLED-已作废)
     */
    private String status;

    /**
     * 替班申请事由
     */
    private String reason;

    /**
     * 确认/生效时间
     */
    private LocalDateTime confirmedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
