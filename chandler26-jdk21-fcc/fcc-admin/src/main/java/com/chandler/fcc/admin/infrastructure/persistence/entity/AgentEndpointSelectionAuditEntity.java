package com.chandler.fcc.admin.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 坐席当前接听终端切换审计事实。
 */
@TableName("fcc_agent_endpoint_selection_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEndpointSelectionAuditEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审计事实主键。 */
    @TableId
    private Long id;

    /** 被操作坐席主键。 */
    private Long agentId;

    /** 操作者登录标识。 */
    private String actor;

    /** 切换前的绑定记录主键。 */
    private Long oldBindingId;

    /** 切换后的绑定记录主键。 */
    private Long newBindingId;

    /** 操作结果。 */
    private String result;

    /** 失败或补充原因。 */
    private String reason;

    /** 事实创建时间。 */
    private LocalDateTime createdAt;
}
