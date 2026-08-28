package com.chandler.learning.agent.reading.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 语境精读历史轻量摘要，文章正文通过详情接口按需加载。 */
@Data
public class ArticleStudySummaryResponse {

    @Schema(description = "主键标识")
    private Long id;
    @Schema(description = "单词本标识")
    private Long wordbookId;
    @Schema(description = "选中词汇列表")
    private List<ArticleStudyWordResponse> selectedWords;
    @Schema(description = "词数范围")
    private String wordCountRange;
    @Schema(description = "难度")
    private String difficulty;
    @Schema(description = "状态")
    private String studyStatus;
    @Schema(description = "业务属性")
    private String currentStage;
    @Schema(description = "练习题总数")
    private Integer practiceTotal;
    @Schema(description = "练习答对数量")
    private Integer practiceCorrect;
    @Schema(description = "练习得分")
    private Integer practiceScore;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
