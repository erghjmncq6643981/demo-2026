package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ReviewSubmitResponse 类。
 */
@Data
public class ReviewSubmitResponse {

    private Long entryId;

    private String normalizedTerm;

    private Integer reviewStage;

    private Integer masteryScore;

    private LocalDateTime nextReviewTime;
}
