package com.chandler.learning.agent.domain.dto;

import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 保存请求。
 */
@Data
@Schema(name = "Agent 保存请求")
public class AgentSaveRequest {

    @NotBlank(message = "Agent 名称不能为空")
    private String name;

    @NotBlank(message = "Agent 编码不能为空")
    private String code;

    private String type = LearningConstants.DEFAULT_AGENT_TYPE;

    private String icon;

    private String description;

    private String systemPrompt;

    private String concisePrompt;

    private String welcomeMessage;

    private String modelProvider;

    private String modelName;

    private Double temperature;

    private Integer maxTokens;

    @Schema(description = "预设指令 JSON")
    private String presetCommands;

    private Integer sequence = LearningConstants.DEFAULT_SEQUENCE;
}
