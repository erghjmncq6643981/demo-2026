package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.time.LocalDateTime;

/** 单词本列表轻量词条，词卡正文通过详情接口按需加载。 */
@Data
public class WordbookEntrySummaryResponse {

    /** 词条主键。 */
    private Long id;
    /** 所属单词本。 */
    private Long wordbookId;
    /** 单词或短语。 */
    private String term;
    /** 归一化词条。 */
    private String normalizedTerm;
    /** 学习状态。 */
    private String status;
    /** 复习阶段。 */
    private Integer reviewStage;
    /** 掌握分数。 */
    private Integer masteryScore;
    /** 最近和下次复习时间。 */
    private LocalDateTime lastReviewTime;
    private LocalDateTime nextReviewTime;
    /** 复习统计。 */
    private Integer reviewCount;
    private Integer correctCount;
    private Integer wrongCount;
    /** 是否存在可用词卡。 */
    private String cardStatus;
    /** 记录创建时间。 */
    private LocalDateTime createTime;
}
