package com.chandler.learning.agent.learning.api;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景学习单元响应。
 */
@Data
public class LearningPlanUnitResponse {

    private Long id;

    private Long planId;

    private Integer unitNo;

    private String title;

    private String scenarioType;

    private String summary;

    private String status;

    private Integer coreWordCount;

    private Integer extendedWordCount;

    private Integer supplementaryWordCount;

    private Integer completedCoreCount;

    private LocalDate recommendedDate;

    /** 场景材料主键，用于加载与材料绑定的学习笔记。 */
    private Long sceneMaterialId;

    /** 是否已经生成可学习的场景材料。 */
    private Boolean materialAvailable;

    /** 日历摘要中的待挑战词汇，不包含词卡、题目和学习详情。 */
    private List<LearningPlanUnitWordSummaryResponse> pendingChallengeWords;

    private String learningText;

    private String translation;

    @com.fasterxml.jackson.annotation.JsonRawValue
    private String material;

    /** 当前材料版本号。 */
    private Integer materialRevision;

    /** 不计入个人进度的场景相关词。 */
    private List<SceneRelatedWordResponse> relatedWords;

    private List<LearningPlanUnitEntryResponse> words;

    private LocalDateTime generatedTime;

    private LocalDateTime completedTime;
}
