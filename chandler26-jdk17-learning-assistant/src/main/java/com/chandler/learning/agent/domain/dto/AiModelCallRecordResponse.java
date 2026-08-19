package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 模型调用审计详情。 */
@Data
public class AiModelCallRecordResponse {

    private Long id;
    private String agentCode;
    private String invocationSceneCode;
    private String provider;
    private String modelName;
    private String requestJson;
    private String responseJson;
    private Boolean success;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private LocalDateTime createTime;
}
