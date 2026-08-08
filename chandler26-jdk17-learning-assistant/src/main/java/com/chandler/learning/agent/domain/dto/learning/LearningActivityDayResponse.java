package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * LearningActivityDayResponse 类。
 */
@Data
public class LearningActivityDayResponse {

    private String date;

    private Integer learnedCount;

    private Integer reviewCount;

    private Integer totalCount;
}
