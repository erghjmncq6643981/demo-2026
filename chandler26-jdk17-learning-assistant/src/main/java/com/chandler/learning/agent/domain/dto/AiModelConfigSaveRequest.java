package com.chandler.learning.agent.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 模型配置保存请求。
 */
@Data
public class AiModelConfigSaveRequest {

    @NotBlank(message = "模型配置名称不能为空")
    private String name;

    @NotBlank(message = "供应商不能为空")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    @NotBlank(message = "Base URL 不能为空")
    private String baseUrl;

    private String chatPath = "/chat/completions";

    private String apiKey;

    private Boolean enabled = true;

    private Boolean isDefault = false;

    private Integer sequence = 0;
}
