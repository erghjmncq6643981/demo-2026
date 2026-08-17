package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景学习单元响应。
 */
@Data
public class LearningPlanUnitResponse {

    private Long id;

    private Long planId;

    private Integer unitNo;

    private String title;

    private String scenarioType;

    private String summary;

    private String status;

    private Integer coreWordCount;

    private Integer extendedWordCount;

    private Integer supplementaryWordCount;

    private Integer completedCoreCount;

    private LocalDate recommendedDate;

    private String learningText;

    private String translation;

    private Object material;

    private List<LearningPlanUnitEntryResponse> words;

    private LocalDateTime generatedTime;

    private LocalDateTime completedTime;
}
