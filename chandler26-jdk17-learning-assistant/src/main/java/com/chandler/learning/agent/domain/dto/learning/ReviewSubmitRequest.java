package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

@Data
public class ReviewSubmitRequest {

    private String result;

    private Integer score;

    private Integer durationSeconds;
}
