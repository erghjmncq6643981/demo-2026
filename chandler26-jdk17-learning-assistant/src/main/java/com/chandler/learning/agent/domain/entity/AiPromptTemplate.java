package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 提示词模板。
 */
@Data
@TableName("ai_prompt_template")
@Schema(name = "AI 提示词模板")
public class AiPromptTemplate {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String code;

    @Schema(description = "system、user、analysis")
    private String type;

    private String tags;

    private String content;

    @Schema(description = "变量定义 JSON")
    private String variables;

    private String description;

    private String exampleInput;

    private String exampleOutput;

    private Boolean publicTemplate;

    private Boolean enabled;

    private Integer sequence;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
