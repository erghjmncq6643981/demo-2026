package com.chandler.learning.agent.ai.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 模型调用记录。
 */
@Data
@TableName("ai_model_call_record")
@Schema(name = "AI 模型调用记录")
public class AiModelCallRecord extends BaseEntity {

    /**
     * id 属性。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * sessionId 属性。
     */
    private Long sessionId;

    /**
     * agentCode 属性。
     */
    private String agentCode;

    /**
     * 本次调用的具体业务场景编码。
     */
    private String invocationSceneCode;

    /**
     * provider 属性。
     */
    private String provider;

    /**
     * modelName 属性。
     */
    private String modelName;

    /**
     * requestJson 属性。
     */
    private String requestJson;

    /**
     * responseJson 属性。
     */
    private String responseJson;

    /**
     * success 属性。
     */
    private Boolean success;

    /**
     * errorMessage 属性。
     */
    private String errorMessage;

    /**
     * promptTokens 属性。
     */
    private Integer promptTokens;

    /**
     * completionTokens 属性。
     */
    private Integer completionTokens;

    /**
     * totalTokens 属性。
     */
    private Integer totalTokens;

    /**
     * latencyMs 属性。
     */
    private Long latencyMs;
}
