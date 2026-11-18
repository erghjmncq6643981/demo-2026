package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 学习活动统计响应。 */
@Data
public class LearningActivityResponse {

    @Schema(description = "统计天数")
    private Integer days;

    @Schema(description = "完成学习的词汇累计数量")
    private Integer learnedTotal;

    @Schema(description = "加入个人单词本的词汇累计数量")
    private Integer wordAddedTotal;

    @Schema(description = "词汇检查累计次数")
    private Integer reviewTotal;

    @Schema(description = "完成场景学习累计次数")
    private Integer sceneCompletedTotal;

    @Schema(description = "完成语境精读累计次数")
    private Integer articleCompletedTotal;

    @Schema(description = "知识反刍累计活跃次数")
    private Integer ruminationTotal;

    @Schema(description = "周期内有效学习行为总次数")
    private Integer totalActivityCount;

    @Schema(description = "周期内有学习记录的活跃天数")
    private Integer activeDays;

    @Schema(description = "当前连续活跃天数（含今天或昨天不断签）")
    private Integer currentStreak;

    @Schema(description = "周期内最长连续活跃天数")
    private Integer longestStreak;

    @Schema(description = "有效学习累计时长，单位秒")
    private Integer studySecondsTotal;

    @Schema(description = "活动日明细")
    private List<LearningActivityDayResponse> items;
}
