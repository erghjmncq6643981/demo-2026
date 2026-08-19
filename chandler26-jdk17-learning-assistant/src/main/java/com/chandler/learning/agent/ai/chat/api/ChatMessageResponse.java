package com.chandler.learning.agent.ai.chat.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息响应。
 */
@Data
public class ChatMessageResponse {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private Integer tokenCount;

    private Long costTime;

    private String modelProvider;

    private String modelName;

    private Integer sequence;

    private LocalDateTime createTime;
}
