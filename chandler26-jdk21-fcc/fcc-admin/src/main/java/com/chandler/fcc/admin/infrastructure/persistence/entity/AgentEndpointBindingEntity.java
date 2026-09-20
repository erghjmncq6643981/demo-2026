package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席话机/分机绑定持久化实体 (fcc_agent_endpoint_binding)
 * <p>
 * 对应 MySQL 中 fcc_agent_endpoint_binding 表，记录坐席与 SIP 分机、手机或 WebRTC 终端的绑定关系及有效期。
 * </p>
 *
 * @author Chandler
 */
@TableName("fcc_agent_endpoint_binding")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AgentEndpointBindingEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 绑定记录雪花主键 ID
     */
    @TableId
    private Long id;

    /**
     * 坐席 ID
     */
    private Long agentId;

    /**
     * 终端类型 (SIP, WEBRTC, MOBILE, PSTN)
     */
    private String endpointType;

    /**
     * 关联分机主键 ID (若绑定为内部分机)
     */
    private Long extensionId;

    /**
     * 终端标识值 (如分机号 1001 或 手机号 13800000000)
     */
    private String endpointValue;

    /**
     * 优先级 (0 为最高优先级)
     */
    private Integer priority;

    /**
     * 绑定状态 (ENABLED, DISABLED)
     */
    private String status;

    /**
     * 有效起始时间
     */
    private LocalDateTime validFrom;

    /**
     * 有效结束时间
     */
    private LocalDateTime validTo;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
