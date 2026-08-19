package com.chandler.learning.agent.domain.dto;

import lombok.Data;

import java.util.List;

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

    /** 结构化响应所使用的解析器，仅结构化调用有值。 */
    private String structuredParser;

    /** 结构化响应成功的解析阶段，例如 raw、balanced、repaired。 */
    private String structuredParseStage;

    /** 结构化响应降级修复项；空数组表示未改写模型文本。 */
    private List<String> structuredRepairs;
}
