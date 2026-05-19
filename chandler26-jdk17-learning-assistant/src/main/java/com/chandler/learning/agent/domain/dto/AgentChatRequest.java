package com.chandler.learning.agent.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Agent 对话请求。
 */
@Data
@Schema(name = "Agent 对话请求")
public class AgentChatRequest {

    @NotBlank(message = "Agent 编码不能为空")
    private String agentCode;

    @Schema(description = "会话 ID，不传则创建新会话")
    private Long sessionId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "业务类型，例如 vocabulary")
    private String businessType;

    @Schema(description = "业务 ID，例如单词 ID")
    private String businessId;

    @Schema(description = "指定模型配置 ID，可选")
    private Long modelConfigId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    @Schema(description = "提示词模板编码，可选")
    private String templateCode;

    @Schema(description = "模板变量")
    private Map<String, Object> variables;
}
