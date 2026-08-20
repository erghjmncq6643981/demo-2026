package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

/**
 * ReviewSubmitRequest 类。
 */
@Data
public class ReviewSubmitRequest {

    private String result;

    private Integer score;

    private Integer durationSeconds;

    private Long wordProgressId;

    private Long planId;

    private Long unitId;

    private String assessmentType;

    private String questionJson;

    private String answerText;

    private String correctAnswer;

    private String checkResult;

    private Double typingAccuracy;

    private Integer hintLevel;

    private Integer attemptCount;

    private Long durationMillis;
}
