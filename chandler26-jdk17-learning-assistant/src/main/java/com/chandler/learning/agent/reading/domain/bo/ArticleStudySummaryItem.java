package com.chandler.learning.agent.reading.domain.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语境精读历史列表投影。文章正文 JSON 不参与列表查询。
 */
@Data
public class ArticleStudySummaryItem {

    /** 主键。 */
    private Long id;
    /** 所属单词本。 */
    private Long wordbookId;
    /** 文章标题。 */
    private String title;
    /** 生成时选择的词汇摘要 JSON。 */
    private String selectedTermsJson;
    /** 文章字数范围。 */
    private String wordCountRange;
    /** 文章难度。 */
    private String difficulty;
    /** 学习状态。 */
    private String studyStatus;
    /** 当前学习阶段。 */
    private String currentStage;
    /** 检测题总数。 */
    private Integer practiceTotal;
    /** 检测答对数。 */
    private Integer practiceCorrect;
    /** 检测得分。 */
    private Integer practiceScore;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
