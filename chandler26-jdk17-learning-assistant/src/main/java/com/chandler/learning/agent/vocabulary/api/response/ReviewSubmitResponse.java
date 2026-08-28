package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ReviewSubmitResponse 类。
 */
@Data
public class ReviewSubmitResponse {

    @Schema(description = "关联业务标识")
    private Long entryId;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "复习阶段")
    private Integer reviewStage;

    @Schema(description = "掌握分数")
    private Integer masteryScore;

    @Schema(description = "时间")
    private LocalDateTime nextReviewTime;
}
