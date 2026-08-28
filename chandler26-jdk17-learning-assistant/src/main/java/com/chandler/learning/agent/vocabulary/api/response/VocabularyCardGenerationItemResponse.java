package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 批量词卡任务单词级结果。
 */
@Data
public class VocabularyCardGenerationItemResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "英文词汇")
    private String term;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "业务状态")
    private String status;

    @Schema(description = "关联业务标识")
    private Long vocabularyId;

    @Schema(description = "尝试次数")
    private Integer attemptCount;

    @Schema(description = "错误信息")
    private String errorMessage;
}
