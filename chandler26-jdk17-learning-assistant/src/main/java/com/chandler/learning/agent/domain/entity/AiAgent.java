package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI Agent 配置。
 */
@Data
@TableName("ai_agent")
@Schema(name = "AI Agent")
public class AiAgent extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "Agent 名称")
    private String name;

    @Schema(description = "Agent 编码")
    private String code;

    @Schema(description = "类型：chat、analysis、assistant")
    private String type;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "完整系统提示词")
    private String systemPrompt;

    @Schema(description = "后续多轮对话使用的精简系统提示词")
    private String concisePrompt;

    @Schema(description = "欢迎语")
    private String welcomeMessage;

    @Schema(description = "模型供应商")
    private String modelProvider;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "温度参数")
    private Double temperature;

    @Schema(description = "最大输出 token")
    private Integer maxTokens;

    @Schema(description = "预设指令 JSON")
    private String presetCommands;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sequence;
}
