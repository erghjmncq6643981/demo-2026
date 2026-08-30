package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 当前业务响应数据。
 */
@Data
public class LearningActivityDayResponse {

    @Schema(description = "日期")
    private String date;

    @Schema(description = "已学习数量")
    private Integer learnedCount;

    @Schema(description = "复习次数")
    private Integer reviewCount;

    @Schema(description = "任务或分页数据总数")
    private Integer totalCount;
}
