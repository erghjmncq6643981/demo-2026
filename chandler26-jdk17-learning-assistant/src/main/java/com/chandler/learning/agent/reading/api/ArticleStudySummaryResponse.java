package com.chandler.learning.agent.reading.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 语境精读历史轻量摘要，文章正文通过详情接口按需加载。 */
@Data
public class ArticleStudySummaryResponse {

    private Long id;
    private Long wordbookId;
    private List<ArticleStudyWordResponse> selectedWords;
    private String wordCountRange;
    private String difficulty;
    private String studyStatus;
    private String currentStage;
    private Integer practiceTotal;
    private Integer practiceCorrect;
    private Integer practiceScore;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
