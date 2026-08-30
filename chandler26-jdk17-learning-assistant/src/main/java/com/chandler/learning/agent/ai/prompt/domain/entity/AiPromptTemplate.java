package com.chandler.learning.agent.ai.prompt.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 提示词模板。
 */
@Data
@TableName("ai_prompt_template")
@Schema(name = "AI 提示词模板")
public class AiPromptTemplate extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务对象名称。 */
    private String name;

    /** 业务编码。 */
    private String code;

    /** 模板类型：system-系统模板，user-用户模板，analysis-分析模板。 */
    @Schema(description = "system、user、analysis")
    private String type;

    /** 词汇标签列表。 */
    private String tags;

    /** 正文内容。 */
    private String content;

    /**
     * 变量定义 JSON。
     */
    @Schema(description = "变量定义 JSON")
    private String variables;

    /** 业务说明。 */
    private String description;

    /** 提示词示例输入。 */
    private String exampleInput;

    /** 提示词示例输出。 */
    private String exampleOutput;

    /** 是否为公共提示词模板。 */
    private Boolean publicTemplate;

    /** 是否启用。 */
    private Boolean enabled;

    /** 展示或执行顺序。 */
    private Integer sequence;
}
