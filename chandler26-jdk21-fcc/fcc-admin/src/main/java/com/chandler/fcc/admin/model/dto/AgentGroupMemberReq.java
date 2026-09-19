package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席加入技能组配置请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席分配进技能组请求参数")
public class AgentGroupMemberReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "技能组ID不能为空")
    @Schema(description = "技能组 ID", example = "1001")
    private Long groupId;

    @NotNull(message = "坐席ID不能为空")
    @Schema(description = "坐席 ID", example = "2001")
    private Long agentId;

    @Schema(description = "组内角色 (LEADER-班长席, MEMBER-普通成员)", example = "MEMBER")
    @Builder.Default
    private String memberRole = "MEMBER";

    @Schema(description = "组内优先级 (数值越小优先级越高)", example = "0")
    @Builder.Default
    private Integer priority = 0;
}
