package com.chandler.learning.agent.ai.model.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 普通学习界面使用的可选模型最小信息。
 */
@Data
public class AiModelOptionResponse {

    @Schema(description = "主键标识")
    private Long id;
    @Schema(description = "名称")
    private String name;
    @Schema(description = "AI 供应商")
    private String provider;
    @Schema(description = "模型名称")
    private String modelName;
    /** 模型展示名称。 */
    @Schema(description = "模型展示名称")
    private String modelDisplayName;
    /** 模型原生上下文窗口，单位为 Token。 */
    @Schema(description = "上下文窗口 Token 上限")
    private Integer contextWindowTokens;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "是否默认")
    private Boolean isDefault;
    @Schema(description = "排序序号")
    private Integer sequence;
}
