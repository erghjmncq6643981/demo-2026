package com.chandler.learning.agent.domain.dto;

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

    private String type = "user";

    private String tags;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private String variables;

    private String description;

    private String exampleInput;

    private String exampleOutput;

    private Boolean publicTemplate = false;

    private Integer sequence = 0;
}
