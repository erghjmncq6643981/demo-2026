package com.chandler.fcc.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通话流程定义汇总展示视图
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通话流程定义视图")
public class FlowDefinitionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程 ID", example = "101")
    private Long id;

    @Schema(description = "流程唯一标识", example = "FLOW-INBOUND")
    private String flowKey;

    @Schema(description = "流程名称", example = "来电流程")
    private String flowName;

    @Schema(description = "流程模式", example = "INBOUND")
    private String modelType;

    @Schema(description = "当前状态", example = "PUBLISHED")
    private String status;

    @Schema(description = "当前激活版本", example = "v1.0.0")
    private String currentVersion;

    @Schema(description = "包含的历史与草稿版本列表")
    private List<FlowVersionVO> versions;
}
