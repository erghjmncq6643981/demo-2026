package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** 单词本列表轻量词条，词卡正文通过详情接口按需加载。 */
@Data
public class WordbookEntrySummaryResponse {

    /** 词条主键。 */
    @Schema(description = "主键标识")
    private Long id;
    /** 所属单词本。 */
    @Schema(description = "单词本标识")
    private Long wordbookId;
    /** 单词或短语。 */
    @Schema(description = "英文词汇")
    private String term;
    /** 归一化词条。 */
    @Schema(description = "标准化词汇")
    private String normalizedTerm;
    /** 词条音标。 */
    @Schema(description = "音标")
    private String phonetic;
    /** 核心释义摘要。 */
    @Schema(description = "释义摘要")
    private String meaningText;
    /** 学习状态。 */
    @Schema(description = "状态")
    private String status;
    /** 复习阶段。 */
    @Schema(description = "复习阶段")
    private Integer reviewStage;
    /** 掌握分数。 */
    @Schema(description = "掌握分数")
    private Integer masteryScore;
    /** 最近和下次复习时间。 */
    @Schema(description = "最近复习时间")
    private LocalDateTime lastReviewTime;
    @Schema(description = "下次复习时间")
    private LocalDateTime nextReviewTime;
    /** 复习统计。 */
    @Schema(description = "数量")
    private Integer reviewCount;
    @Schema(description = "答对次数")
    private Integer correctCount;
    @Schema(description = "答错次数")
    private Integer wrongCount;
    /** 是否存在可用词卡。 */
    @Schema(description = "状态")
    private String cardStatus;
    /** 记录创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
