package com.chandler.learning.agent.ai.chat.domain.entity;

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

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** AI 会话 ID。 */
    private Long sessionId;

    /** Agent 编码。 */
    private String agentCode;

    /**
     * 本次调用的具体业务场景编码。
     */
    private String invocationSceneCode;

    /** 模型供应商。 */
    private String provider;

    /** 模型名称。 */
    private String modelName;

    /** 模型请求摘要 JSON。 */
    private String requestJson;

    /** 模型响应审计 JSON。 */
    private String responseJson;

    /** 操作是否成功。 */
    private Boolean success;

    /** 错误原因。 */
    private String errorMessage;

    /** 模型输入 Token 数。 */
    private Integer promptTokens;

    /** 模型输出 Token 数。 */
    private Integer completionTokens;

    /** 模型调用 Token 总数。 */
    private Integer totalTokens;

    /** 调用延迟，单位毫秒。 */
    private Long latencyMs;
}
