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

    /** 当前配置是否属于可用于新任务的模型枚举。 */
    private Boolean supported;

    /** 供应商展示名称。 */
    private String providerName;

    /** 模型展示名称。 */
    private String modelDisplayName;

    /** API 协议编码。 */
    private String apiProtocol;

    /** 调用前请求适配器编码。 */
    private String requestAdapter;

    /** 调用后结构化响应解析器编码。 */
    private String responseParser;

    /** 模型原生上下文窗口，单位为 Token。 */
    private Integer contextWindowTokens;

    /** 模型单次最大输出，单位为 Token。 */
    private Integer maxOutputTokens;

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
