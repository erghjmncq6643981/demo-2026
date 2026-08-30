package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 复习响应数据。
 */
@Data
public class ReviewSubmitResponse {

    @Schema(description = "单词本词条 ID")
    private Long entryId;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "复习阶段")
    private Integer reviewStage;

    @Schema(description = "掌握分数")
    private Integer masteryScore;

    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;
}
