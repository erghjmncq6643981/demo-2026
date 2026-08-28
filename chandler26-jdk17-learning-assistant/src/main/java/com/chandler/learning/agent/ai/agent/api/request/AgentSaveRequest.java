package com.chandler.learning.agent.ai.agent.api.request;

import com.chandler.learning.agent.ai.agent.domain.enums.AiAgentType;
import com.chandler.learning.agent.common.constant.CommonConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Schema(description = "业务编码")
    private String code;

    @Schema(description = "Agent 类型：chat-对话，analysis-分析，assistant-助手")
    private String type = AiAgentType.CHAT.getCode();

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "精简提示词")
    private String concisePrompt;

    @Schema(description = "欢迎语")
    private String welcomeMessage;

    @NotNull(message = "请选择 Agent 使用的模型配置")
    @Schema(description = "绑定的模型配置 ID")
    private Long modelConfigId;

    @Schema(description = "兼容字段，由后端根据模型配置生成", accessMode = Schema.AccessMode.READ_ONLY)
    private String modelProvider;

    @Schema(description = "兼容字段，由后端根据模型配置生成", accessMode = Schema.AccessMode.READ_ONLY)
    private String modelName;

    @Schema(description = "生成温度")
    private Double temperature;

    @Schema(description = "最大 Token 数")
    private Integer maxTokens;

    @Schema(description = "预设指令 JSON")
    private String presetCommands;

    @Schema(description = "排序序号")
    private Integer sequence = CommonConstants.DEFAULT_SEQUENCE;
}
