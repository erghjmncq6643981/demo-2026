package com.chandler.learning.agent.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话。
 */
@Data
@TableName("ai_chat_session")
@Schema(name = "AI 对话会话")
public class AiChatSession {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String agentCode;

    private String businessType;

    private String businessId;

    private String sceneCode;

    private String title;

    @Schema(description = "会话级变量 JSON")
    private String variablesJson;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
