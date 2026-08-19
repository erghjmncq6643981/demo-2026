package com.chandler.learning.agent.ai.prompt.api;

import com.chandler.learning.agent.ai.prompt.domain.PromptTemplateType;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提示词模板保存请求。
 */
@Data
public class PromptTemplateSaveRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    @NotBlank(message = "模板编码不能为空")
    private String code;

    @Schema(description = "模板类型：system-系统提示词，user-用户提示词，analysis-分析提示词")
    private String type = PromptTemplateType.USER.getCode();

    private String tags;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private String variables;

    private String description;

    private String exampleInput;

    private String exampleOutput;

    private Boolean publicTemplate = false;

    private Integer sequence = LearningConstants.DEFAULT_SEQUENCE;
}
