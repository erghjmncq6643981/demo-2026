package com.chandler.learning.agent.domain.dto;

import lombok.Data;

/**
 * 模型调用响应。
 */
@Data
public class ModelChatResponse {

    private String content;

    private String responseJson;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 供应商结束原因，例如 stop、length、content_filter。 */
    private String finishReason;
}
