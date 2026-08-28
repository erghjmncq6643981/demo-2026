package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * LearningActivityDayResponse 类。
 */
@Data
public class LearningActivityDayResponse {

    @Schema(description = "日期")
    private String date;

    @Schema(description = "已学习数量")
    private Integer learnedCount;

    @Schema(description = "复习次数")
    private Integer reviewCount;

    @Schema(description = "总数量")
    private Integer totalCount;
}
