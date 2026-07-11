package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话消息。
 */
@Data
@TableName("ai_chat_message")
@Schema(name = "AI 对话消息")
public class AiChatMessage extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    @Schema(description = "system、user、assistant")
    private String role;

    private String content;

    private Integer tokenCount;

    private Long costTime;

    private String modelProvider;

    private String modelName;

    private Integer sequence;
}
