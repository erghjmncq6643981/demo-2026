package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 提示词模板。
 */
@Data
@TableName("ai_prompt_template")
@Schema(name = "AI 提示词模板")
public class AiPromptTemplate extends BaseEntity {

    /**
     * id 属性。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * name 属性。
     */
    private String name;

    /**
     * code 属性。
     */
    private String code;

    /**
     * system、user、analysis。
     */
    @Schema(description = "system、user、analysis")
    private String type;

    /**
     * tags 属性。
     */
    private String tags;

    /**
     * content 属性。
     */
    private String content;

    /**
     * 变量定义 JSON。
     */
    @Schema(description = "变量定义 JSON")
    private String variables;

    /**
     * description 属性。
     */
    private String description;

    /**
     * exampleInput 属性。
     */
    private String exampleInput;

    /**
     * exampleOutput 属性。
     */
    private String exampleOutput;

    /**
     * publicTemplate 属性。
     */
    private Boolean publicTemplate;

    /**
     * enabled 属性。
     */
    private Boolean enabled;

    /**
     * sequence 属性。
     */
    private Integer sequence;
}
