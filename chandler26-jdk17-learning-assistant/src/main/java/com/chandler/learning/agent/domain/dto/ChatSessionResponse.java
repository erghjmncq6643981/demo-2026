package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话响应。
 */
@Data
public class ChatSessionResponse {

    private Long id;

    private String agentCode;

    private String businessType;

    private String businessId;

    private String title;

    private Integer messageCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
