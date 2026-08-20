package com.chandler.learning.agent.learning.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景词汇检查结果。
 */
@Data
public class LearningAssessmentSubmitResponse {

    private Long unitEntryId;

    private String assessmentType;

    private Boolean correct;

    private String correctAnswer;

    private Double typingAccuracy;

    private String learningState;

    private Integer recognitionScore;

    private Integer spellingScore;

    private Integer completedCoreCount;

    private Integer coreWordCount;

    private Boolean unitReadyToComplete;

    private LocalDateTime nextReviewTime;
}
