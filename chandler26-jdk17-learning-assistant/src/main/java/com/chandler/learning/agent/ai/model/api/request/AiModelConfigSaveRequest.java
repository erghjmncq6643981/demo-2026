package com.chandler.learning.agent.ai.model.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.chandler.learning.agent.ai.gateway.constant.AiGatewayConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 模型配置保存请求。
 */
@Data
public class AiModelConfigSaveRequest {

    @NotBlank(message = "模型配置名称不能为空")
    @Schema(description = "名称")
    private String name;

    @NotBlank(message = "供应商不能为空")
    @Schema(description = "AI 供应商")
    private String provider;

    @NotBlank(message = "模型名称不能为空")
    @Schema(description = "模型名称")
    private String modelName;

    @NotBlank(message = "Base URL 不能为空")
    @Schema(description = "服务地址")
    private String baseUrl;

    @Schema(description = "聊天接口路径")
    private String chatPath = AiGatewayConstants.DEFAULT_CHAT_PATH;

    @Schema(description = "模型 API Key")
    private String apiKey;

    @Schema(description = "是否启用")
    private Boolean enabled = true;

    @Schema(description = "是否满足该条件")
    private Boolean isDefault = false;

    @Schema(description = "排序序号")
    private Integer sequence = CommonConstants.DEFAULT_SEQUENCE;
}
