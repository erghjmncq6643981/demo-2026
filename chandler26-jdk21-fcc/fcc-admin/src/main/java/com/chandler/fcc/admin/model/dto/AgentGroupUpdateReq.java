package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 坐席技能组修改请求 DTO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "技能组修改请求")
public class AgentGroupUpdateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "技能组 ID 不能为空")
    @Schema(description = "技能组 ID", example = "1")
    private Long id;

    @Schema(description = "技能组编码")
    private String groupCode;

    @Schema(description = "技能组名称")
    private String groupName;

    @Schema(description = "组类型 (COMPANY, CENTER, SKILL)")
    private String groupType;

    @Schema(description = "话务分发路由策略 (LONGEST_IDLE, ROUND_ROBIN, PRIORITY)")
    private String routingStrategy;

    @Schema(description = "状态 (ENABLED, DISABLED)")
    private String status;
}
