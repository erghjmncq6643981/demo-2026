package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话会话。
 */
@Data
@TableName("ai_chat_session")
@Schema(name = "AI 对话会话")
public class AiChatSession extends BaseEntity {

    /**
     * id 属性。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * userId 属性。
     */
    private Long userId;

    /**
     * agentCode 属性。
     */
    private String agentCode;

    /**
     * businessType 属性。
     */
    private String businessType;

    /**
     * businessId 属性。
     */
    private String businessId;

    /**
     * sceneCode 属性。
     */
    private String sceneCode;

    /**
     * title 属性。
     */
    private String title;

    /**
     * 会话级变量 JSON。
     */
    @Schema(description = "会话级变量 JSON")
    private String variablesJson;
}
