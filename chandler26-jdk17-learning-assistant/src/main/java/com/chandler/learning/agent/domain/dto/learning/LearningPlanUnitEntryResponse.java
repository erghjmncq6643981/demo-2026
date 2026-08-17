package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.util.List;

/**
 * 场景单元词汇响应。
 */
@Data
public class LearningPlanUnitEntryResponse {

    private Long id;

    private Long catalogEntryId;

    private Long wordbookEntryId;

    private Long wordProgressId;

    private Integer sourceOrder;

    private String term;

    private String normalizedTerm;

    private String phonetic;

    private String meaning;

    private String contextMeaning;

    private String tier;

    private String masteryRequirement;

    private List<String> acceptedSpellings;

    private Object assessment;

    private List<String> passedAssessments;

    private Boolean firstLearning;

    private String learningState;

    private Integer recognitionScore;

    private Integer spellingScore;

    private String cardStatus;
}
