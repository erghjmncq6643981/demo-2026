package com.chandler.learning.agent.ai.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话消息。
 */
@Data
@TableName("ai_chat_message")
@Schema(name = "AI 对话消息")
public class AiChatMessage extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** AI 会话 ID。 */
    private Long sessionId;

    /** 消息角色：system-系统提示词，user-用户消息，assistant-模型回复。 */
    @Schema(description = "system、user、assistant")
    private String role;

    /** 正文内容。 */
    private String content;

    /** Token 数量。 */
    private Integer tokenCount;

    /** 处理耗时，单位毫秒。 */
    private Long costTime;

    /** 模型供应商编码。 */
    private String modelProvider;

    /** 模型名称。 */
    private String modelName;

    /** 展示或执行顺序。 */
    private Integer sequence;
}
