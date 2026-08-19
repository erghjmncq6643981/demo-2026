package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * 用户学习设置响应。
 */
@Data
public class LearningSettingsResponse {

    /**
     * 学习时默认使用的 Agent 编码。
     */
    private String agentCode;

    /**
     * 学习时默认使用的提示词模板编码。
     */
    private String templateCode;
}
