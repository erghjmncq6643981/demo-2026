package com.chandler.learning.agent.ai.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 对话会话。
 */
@Data
@TableName("ai_chat_session")
@Schema(name = "AI 对话会话")
public class AiChatSession extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** Agent 编码。 */
    private String agentCode;

    /** 关联业务类型。 */
    private String businessType;

    /** 关联业务数据 ID。 */
    private String businessId;

    /** 学习场景编码。 */
    private String sceneCode;

    /** 标题。 */
    private String title;

    /**
     * 会话级变量 JSON。
     */
    @Schema(description = "会话级变量 JSON")
    private String variablesJson;
}
