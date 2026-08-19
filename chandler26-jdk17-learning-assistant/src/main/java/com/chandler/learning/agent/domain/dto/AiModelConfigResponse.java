package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置响应。
 */
@Data
public class AiModelConfigResponse {

    private Long id;

    private String name;

    private String provider;

    private String modelName;

    private String baseUrl;

    private String chatPath;

    private String apiKeyMasked;

    private Boolean enabled;

    private Boolean isDefault;

    private Integer sequence;

    /** 累计模型调用次数。 */
    private Long callCount;

    /** 成功调用次数。 */
    private Long successCount;

    /** 失败调用次数。 */
    private Long failedCount;

    /** 累计消耗 Token。 */
    private Long totalTokens;

    /** 平均模型响应耗时，单位毫秒。 */
    private Long averageLatencyMs;

    /** 最近一次调用时间。 */
    private LocalDateTime lastCallTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
