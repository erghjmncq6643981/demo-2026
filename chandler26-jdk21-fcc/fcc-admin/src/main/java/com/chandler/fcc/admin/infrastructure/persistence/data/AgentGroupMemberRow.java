package com.chandler.fcc.admin.infrastructure.persistence.data;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 组织子树成员分页查询结果。
 *
 * <p>同一坐席同时属于多个子节点时，查询只保留距离所选节点最近的一条成员关系。</p>
 */
@Data
public class AgentGroupMemberRow {

    /** 成员关系主键。 */
    private Long membershipId;

    /** 成员关系实际所属组织节点主键。 */
    private Long groupId;

    /** 成员关系实际所属组织节点名称。 */
    private String groupName;

    /** 坐席主键。 */
    private Long agentId;

    /** 坐席工号。 */
    private String workNo;

    /** 坐席姓名。 */
    private String agentName;

    /** 坐席联系电话。 */
    private String phoneNumber;

    /** 坐席在实际所属组内的角色。 */
    private String memberRole;

    /** 坐席在实际所属组内的分发优先级。 */
    private Integer priority;

    /** 坐席系统角色。 */
    private String roleCode;

    /** 坐席账号状态。 */
    private String status;

    /** 成员关系创建时间。 */
    private LocalDateTime createdAt;
}
