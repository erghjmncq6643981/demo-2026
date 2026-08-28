package com.chandler.learning.agent.ai.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI Agent 配置。
 */
@Data
@TableName("ai_agent")
@Schema(name = "AI Agent")
public class AiAgent extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * Agent 名称。
     */
    @Schema(description = "Agent 名称")
    private String name;

    /**
     * Agent 编码。
     */
    @Schema(description = "Agent 编码")
    private String code;

    /**
     * 类型：chat、analysis、assistant。
     */
    @Schema(description = "类型：chat、analysis、assistant")
    private String type;

    /**
     * 图标。
     */
    @Schema(description = "图标")
    private String icon;

    /**
     * 描述。
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 完整系统提示词。
     */
    @Schema(description = "完整系统提示词")
    private String systemPrompt;

    /**
     * 后续多轮对话使用的精简系统提示词。
     */
    @Schema(description = "后续多轮对话使用的精简系统提示词")
    private String concisePrompt;

    /**
     * 欢迎语。
     */
    @Schema(description = "欢迎语")
    private String welcomeMessage;

    /**
     * 绑定的模型配置 ID，决定 Agent 实际使用的 API 地址、密钥和模型。
     */
    @Schema(description = "绑定的模型配置 ID")
    private Long modelConfigId;

    /**
     * 模型供应商。
     */
    @Schema(description = "模型供应商")
    private String modelProvider;

    /**
     * 模型名称。
     */
    @Schema(description = "模型名称")
    private String modelName;

    /** 模型配置展示名称，不写入 Agent 表。 */
    @TableField(exist = false)
    private String modelConfigName;

    /** 绑定的模型配置当前是否可用，不写入 Agent 表。 */
    @TableField(exist = false)
    private Boolean modelConfigEnabled;

    /**
     * 温度参数。
     */
    @Schema(description = "温度参数")
    private Double temperature;

    /**
     * 最大输出 token。
     */
    @Schema(description = "最大输出 token")
    private Integer maxTokens;

    /**
     * 预设指令 JSON。
     */
    @Schema(description = "预设指令 JSON")
    private String presetCommands;

    /**
     * 是否启用。
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 排序。
     */
    @Schema(description = "排序")
    private Integer sequence;
}
