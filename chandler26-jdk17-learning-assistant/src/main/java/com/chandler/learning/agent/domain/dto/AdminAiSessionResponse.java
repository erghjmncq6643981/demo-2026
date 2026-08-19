package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 系统管理中的 AI 会话摘要。 */
@Data
public class AdminAiSessionResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String title;
    private String agentCode;
    private String businessType;
    private String businessId;
    private String sceneCode;
    private Integer messageCount;
    private Integer callCount;
    private Integer successCount;
    private Integer failedCount;
    private Long totalTokens;
    private Long averageLatencyMs;
    private String lastProvider;
    private String lastModelName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
