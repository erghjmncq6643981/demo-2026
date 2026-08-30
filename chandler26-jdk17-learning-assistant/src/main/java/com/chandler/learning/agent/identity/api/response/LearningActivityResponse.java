package com.chandler.learning.agent.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * 当前业务响应数据。
 */
@Data
public class LearningActivityResponse {

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "累计学习词汇数量")
    private Integer learnedTotal;

    @Schema(description = "累计复习词汇数量")
    private Integer reviewTotal;

    @Schema(description = "分页数据列表")
    private List<LearningActivityDayResponse> items;
}
