package com.chandler.learning.agent.ai.model.domain.bo;

import lombok.Data;

import java.time.LocalDateTime;

/** 模型调用聚合指标。 */
@Data
public class AiModelUsageSummary {

    private String provider;
    private String modelName;
    private Long callCount;
    private Long successCount;
    private Long failedCount;
    private Long totalTokens;
    private Long averageLatencyMs;
    private LocalDateTime lastCallTime;
}
