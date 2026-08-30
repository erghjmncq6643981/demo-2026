package com.chandler.learning.agent.vocabulary.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按当前或近期场景生成词卡请求。
 */
@Data
public class VocabularyCardGenerationRequest {

    @Schema(description = "批处理数量")
    private Integer batchSize;

    @Schema(description = "模型配置标识")
    private Long modelConfigId;

    /** immediate、scheduled、low_cost_window。 */
    @Schema(description = "执行方式")
    private String executionMode;

    /** 指定执行时间，按后端时区解析。 */
    @Schema(description = "计划执行时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "优先级")
    private Integer priority;
}
