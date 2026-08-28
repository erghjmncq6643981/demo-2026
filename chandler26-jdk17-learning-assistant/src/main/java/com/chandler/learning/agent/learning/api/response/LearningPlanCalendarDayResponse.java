package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习计划某一天的日历汇总。
 */
@Data
public class LearningPlanCalendarDayResponse {

    @Schema(description = "日期")
    private LocalDate date;

    @Schema(description = "计划词汇数量")
    private Integer plannedWordCount;

    @Schema(description = "数量")
    private Integer pendingChallengeCount;

    @Schema(description = "已生成场景单元数量")
    private Integer generatedUnitCount;

    @Schema(description = "数量")
    private Integer completedUnitCount;

    @Schema(description = "逾期复习数量")
    private Integer overdueCount;

    /** 是否正在后台生成场景材料。 */
    @Schema(description = "是否正在生成")
    private Boolean generating;

    @Schema(description = "场景单元列表")
    private List<LearningPlanUnitResponse> units;
}
