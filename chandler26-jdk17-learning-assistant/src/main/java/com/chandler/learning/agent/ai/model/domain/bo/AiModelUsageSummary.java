package com.chandler.learning.agent.ai.model.domain.bo;

import lombok.Data;

import java.time.LocalDateTime;

/** 模型调用聚合指标。 */
@Data
public class AiModelUsageSummary {

    /** 模型供应商。 */
    private String provider;
    /** 模型名称。 */
    private String modelName;
    /** 模型调用总次数。 */
    private Long callCount;
    /** 处理成功数量。 */
    private Long successCount;
    /** 处理失败数量。 */
    private Long failedCount;
    /** 模型调用 Token 总数。 */
    private Long totalTokens;
    /** 平均调用延迟，单位毫秒。 */
    private Long averageLatencyMs;
    /** 最近模型调用时间。 */
    private LocalDateTime lastCallTime;
}
