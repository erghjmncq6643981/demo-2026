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

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
