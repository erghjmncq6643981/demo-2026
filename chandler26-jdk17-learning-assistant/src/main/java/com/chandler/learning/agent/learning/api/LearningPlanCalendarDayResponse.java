package com.chandler.learning.agent.learning.api;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习计划某一天的日历汇总。
 */
@Data
public class LearningPlanCalendarDayResponse {

    private LocalDate date;

    private Integer plannedWordCount;

    private Integer pendingChallengeCount;

    private Integer generatedUnitCount;

    private Integer completedUnitCount;

    private Integer overdueCount;

    /** 是否正在后台生成场景材料。 */
    private Boolean generating;

    private List<LearningPlanUnitResponse> units;
}
