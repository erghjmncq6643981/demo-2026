package com.chandler.learning.agent.domain.dto.learning;

import lombok.Data;

/**
 * ReviewSubmitRequest 类。
 */
@Data
public class ReviewSubmitRequest {

    private String result;

    private Integer score;

    private Integer durationSeconds;
}
