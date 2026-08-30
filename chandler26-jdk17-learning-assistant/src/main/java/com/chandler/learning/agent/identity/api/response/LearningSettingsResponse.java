package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 用户学习设置响应。
 */
@Data
public class LearningSettingsResponse {

    /**
     * 学习时默认使用的 Agent 编码。
     */
    @Schema(description = "Agent 编码")
    private String agentCode;

    /**
     * 学习时默认使用的提示词模板编码。
     */
    @Schema(description = "提示词模板编码")
    private String templateCode;
}
