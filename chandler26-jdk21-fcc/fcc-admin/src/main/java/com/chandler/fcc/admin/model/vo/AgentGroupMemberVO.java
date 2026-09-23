package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 坐席技能组成员视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "技能组成员视图")
public class AgentGroupMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "成员关联记录 ID")
    private String id;

    @Schema(description = "技能组 ID")
    private String groupId;

    @Schema(description = "成员实际所属组织或技能组名称")
    private String groupName;

    @Schema(description = "坐席 ID")
    private String agentId;

    @Schema(description = "工号")
    private String workNo;

    @Schema(description = "坐席姓名")
    private String agentName;

    @Schema(description = "联系手机号")
    private String phoneNumber;

    @Schema(description = "组内角色 (LEADER-班长/组长, MEMBER-普通坐席)")
    private String memberRole;

    @Schema(description = "分发优先级 (数值越小优先级越高)")
    private Integer priority;

    @Schema(description = "系统角色编码")
    private String roleCode;

    @Schema(description = "坐席状态")
    private String status;

    @Schema(description = "加入时间")
    private LocalDateTime createdAt;
}
