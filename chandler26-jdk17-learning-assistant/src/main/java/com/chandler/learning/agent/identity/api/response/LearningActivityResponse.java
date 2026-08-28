package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * LearningActivityResponse 类。
 */
@Data
public class LearningActivityResponse {

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "总数量")
    private Integer learnedTotal;

    @Schema(description = "总数量")
    private Integer reviewTotal;

    @Schema(description = "列表数据")
    private List<LearningActivityDayResponse> items;
}
