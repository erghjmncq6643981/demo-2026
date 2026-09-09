package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 学习活动按日返回数据。 */
@Data
public class LearningActivityDayResponse {

    @Schema(description = "活动日期")
    private String date;

    @Schema(description = "完成学习的词汇数量")
    private Integer learnedCount;

    @Schema(description = "加入个人单词本的词汇数量")
    private Integer wordAddedCount;

    @Schema(description = "词汇检查次数")
    private Integer reviewCount;

    @Schema(description = "完成场景学习次数")
    private Integer sceneCompletedCount;

    @Schema(description = "完成语境精读次数")
    private Integer articleCompletedCount;

    @Schema(description = "有效学习时长，单位秒")
    private Integer studySeconds;

    @Schema(description = "当天有效学习行为总数")
    private Integer totalCount;
}
