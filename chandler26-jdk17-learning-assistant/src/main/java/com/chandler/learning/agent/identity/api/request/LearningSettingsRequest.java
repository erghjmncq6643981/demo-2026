package com.chandler.learning.agent.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户学习设置保存请求。
 */
@Data
public class LearningSettingsRequest {

    /**
     * 学习时默认使用的 Agent 编码。
     */
    @NotBlank
    @Schema(description = "编码")
    private String agentCode;

    /**
     * 学习时默认使用的提示词模板编码。
     */
    @NotBlank
    @Schema(description = "提示词模板编码")
    private String templateCode;
}
