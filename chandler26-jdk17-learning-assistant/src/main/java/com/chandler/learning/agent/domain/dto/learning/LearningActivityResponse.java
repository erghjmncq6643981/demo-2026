package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

import java.util.List;

/**
 * LearningActivityResponse 类。
 */
@Data
public class LearningActivityResponse {

    private Integer days;

    private Integer learnedTotal;

    private Integer reviewTotal;

    private List<LearningActivityDayResponse> items;
}
