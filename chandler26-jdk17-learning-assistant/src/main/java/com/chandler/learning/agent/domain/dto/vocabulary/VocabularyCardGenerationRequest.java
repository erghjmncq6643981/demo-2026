package com.chandler.learning.agent.domain.dto.vocabulary;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 按当前或近期场景生成词卡请求。
 */
@Data
public class VocabularyCardGenerationRequest {

    private Integer batchSize;

    private Long modelConfigId;

    /** immediate、scheduled、low_cost_window。 */
    private String executionMode;

    /** 指定执行时间，按后端时区解析。 */
    private LocalDateTime scheduledTime;

    private Integer priority;
}
