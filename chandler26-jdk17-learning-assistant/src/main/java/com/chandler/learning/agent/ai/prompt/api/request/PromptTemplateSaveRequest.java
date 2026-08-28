package com.chandler.learning.agent.ai.prompt.api.request;

import com.chandler.learning.agent.ai.prompt.domain.enums.PromptTemplateType;
import com.chandler.learning.agent.common.constant.CommonConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提示词模板保存请求。
 */
@Data
public class PromptTemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    @Schema(description = "名称")
    private String name;

    @NotBlank(message = "模板编码不能为空")
    @Schema(description = "业务编码")
    private String code;

    @Schema(description = "模板类型：system-系统提示词，user-用户提示词，analysis-分析提示词")
    private String type = PromptTemplateType.USER.getCode();

    @Schema(description = "标签列表")
    private String tags;

    @NotBlank(message = "模板内容不能为空")
    @Schema(description = "内容")
    private String content;

    @Schema(description = "提示词变量列表")
    private String variables;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "业务属性")
    private String exampleInput;

    @Schema(description = "业务属性")
    private String exampleOutput;

    @Schema(description = "业务属性")
    private Boolean publicTemplate = false;

    @Schema(description = "排序序号")
    private Integer sequence = CommonConstants.DEFAULT_SEQUENCE;
}
