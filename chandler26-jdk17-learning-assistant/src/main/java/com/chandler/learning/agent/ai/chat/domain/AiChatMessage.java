package com.chandler.learning.agent.ai.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话消息。
 */
@Data
@TableName("ai_chat_message")
@Schema(name = "AI 对话消息")
public class AiChatMessage extends BaseEntity {

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
     * system、user、assistant。
     */
    @Schema(description = "system、user、assistant")
    private String role;

    /**
     * content 属性。
     */
    private String content;

    /**
     * tokenCount 属性。
     */
    private Integer tokenCount;

    /**
     * costTime 属性。
     */
    private Long costTime;

    /**
     * modelProvider 属性。
     */
    private String modelProvider;

    /**
     * modelName 属性。
     */
    private String modelName;

    /**
     * sequence 属性。
     */
    private Integer sequence;
}
