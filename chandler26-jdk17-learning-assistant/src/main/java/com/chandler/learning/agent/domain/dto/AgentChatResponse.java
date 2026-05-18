package com.chandler.learning.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent 对话响应。
 */
@Data
@Schema(name = "Agent 对话响应")
public class AgentChatResponse {

    private Long sessionId;

    private String agentCode;

    private String modelProvider;

    private String modelName;

    private String content;

    private Integer tokenUsage;

    private Long costTime;
}
