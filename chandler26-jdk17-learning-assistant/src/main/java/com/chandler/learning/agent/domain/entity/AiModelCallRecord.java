package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模型调用记录。
 */
@Data
@TableName("ai_model_call_record")
@Schema(name = "AI 模型调用记录")
public class AiModelCallRecord extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private String agentCode;

    private String provider;

    private String modelName;

    private String requestJson;

    private String responseJson;

    private Boolean success;

    private String errorMessage;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long latencyMs;
}
