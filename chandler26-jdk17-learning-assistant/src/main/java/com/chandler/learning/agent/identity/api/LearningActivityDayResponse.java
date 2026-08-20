package com.chandler.learning.agent.identity.api;

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
