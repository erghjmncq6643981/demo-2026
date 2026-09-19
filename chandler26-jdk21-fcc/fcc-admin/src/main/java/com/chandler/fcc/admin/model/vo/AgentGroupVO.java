package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 坐席技能组信息视图对象 VO
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "坐席技能组展示视图")
public class AgentGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "技能组 ID", example = "1001")
    private Long id;

    @Schema(description = "父技能组 ID", example = "0")
    private Long parentId;

    @Schema(description = "技能组编码", example = "TECH_SUPPORT")
    private String groupCode;

    @Schema(description = "技能组名称", example = "技术专家支持组")
    private String groupName;

    @Schema(description = "组类型", example = "SKILL")
    private String groupType;

    @Schema(description = "路由策略", example = "LONGEST_IDLE")
    private String routingStrategy;

    @Schema(description = "状态 (ENABLED, DISABLED)", example = "ENABLED")
    private String status;

    @Schema(description = "组内成员总人数", example = "5")
    private Integer memberCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
