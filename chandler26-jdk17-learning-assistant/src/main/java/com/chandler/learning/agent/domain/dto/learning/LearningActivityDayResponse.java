package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class LearningActivityDayResponse {

    private String date;

    private Integer learnedCount;

    private Integer reviewCount;

    private Integer totalCount;
}
