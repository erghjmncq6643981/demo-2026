package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席技能组成员属性修改请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "技能组成员属性修改请求")
public class AgentGroupMemberUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "组内角色 (LEADER-班长/组长, MEMBER-普通坐席)")
    private String memberRole;

    @Schema(description = "分发优先级 (数值越小优先级越高)")
    private Integer priority;
}
