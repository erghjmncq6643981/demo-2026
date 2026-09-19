package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务通话流程主数据实体 (fcc_flow_definition)
 *
 * @author Chandler
 */
@TableName("fcc_flow_definition")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class FlowDefinitionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long tenantId;

    /**
     * 流程唯一标识 (如 FLOW-INBOUND, FLOW-OUTBOUND, FLOW-PHONE-DIRECT)
     */
    private String flowKey;

    /**
     * 流程展示名称 (如 来电流程, 外呼, 话机直接外呼)
     */
    private String flowName;

    /**
     * 流模型类型 (如 INBOUND, OUTBOUND, PHONE_DIRECT)
     */
    private String modelType;

    /**
     * 状态 (DRAFT, PUBLISHED)
     */
    private String status;

    /**
     * 当前线上生效版本号 (如 1 对应 v1.0.0)
     */
    private Integer currentVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
