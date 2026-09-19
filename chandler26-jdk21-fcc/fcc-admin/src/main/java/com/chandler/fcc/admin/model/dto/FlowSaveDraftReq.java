package com.chandler.fcc.admin.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通话流程保存草稿入参
 *
 * @author Chandler
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流程保存草稿入参")
public class FlowSaveDraftReq implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "流程版本号", example = "v1.1.0")
    private String version;

    @Schema(description = "呼入路由模式 (DID_DIRECT, RULE_ENGINE, HTTP_CALLBACK)", example = "HTTP_CALLBACK")
    private String routeMode;

    @Schema(description = "流程编排配置 JSON 字符串", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流程编排配置不能为空")
    private String definitionJson;
}
