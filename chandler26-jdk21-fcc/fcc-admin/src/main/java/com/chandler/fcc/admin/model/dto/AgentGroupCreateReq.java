package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席技能组创建请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席技能组创建请求参数")
public class AgentGroupCreateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "技能组编码不能为空")
    @Schema(description = "技能组唯一编码", example = "TECH_SUPPORT")
    private String groupCode;

    @NotBlank(message = "技能组名称不能为空")
    @Schema(description = "技能组中文名称", example = "技术专家支持组")
    private String groupName;

    @Schema(description = "组类型 (SKILL, BUSINESS, VIRTUAL)", example = "SKILL")
    @Builder.Default
    private String groupType = "SKILL";

    @Schema(description = "路由策略 (LONGEST_IDLE, ROUND_ROBIN, PRIORITY, RANDOM)", example = "LONGEST_IDLE")
    @Builder.Default
    private String routingStrategy = "LONGEST_IDLE";

    @Schema(description = "父技能组 ID", example = "0")
    private Long parentId;
}
