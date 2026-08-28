package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景学习单元响应。
 */
@Data
public class LearningPlanUnitResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "学习计划标识")
    private Long planId;

    @Schema(description = "场景单元序号")
    private Integer unitNo;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "场景类型")
    private String scenarioType;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "业务状态")
    private String status;

    @Schema(description = "核心词汇数量")
    private Integer coreWordCount;

    @Schema(description = "数量")
    private Integer extendedWordCount;

    @Schema(description = "补充词汇数量")
    private Integer supplementaryWordCount;

    @Schema(description = "已完成核心词数量")
    private Integer completedCoreCount;

    @Schema(description = "建议学习日期")
    private LocalDate recommendedDate;

    /** 场景材料主键，用于加载与材料绑定的学习笔记。 */
    @Schema(description = "关联业务标识")
    private Long sceneMaterialId;

    /** 是否已经生成可学习的场景材料。 */
    @Schema(description = "场景材料是否可用")
    private Boolean materialAvailable;

    /** 日历摘要中的待挑战词汇，不包含词卡、题目和学习详情。 */
    @Schema(description = "待挑战词汇列表")
    private List<LearningPlanUnitWordSummaryResponse> pendingChallengeWords;

    @Schema(description = "学习文章")
    private String learningText;

    @Schema(description = "中文翻译")
    private String translation;

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Schema(description = "场景材料内容")
    private String material;

    /** 当前材料版本号。 */
    @Schema(description = "材料版本号")
    private Integer materialRevision;

    /** 不计入个人进度的场景相关词。 */
    @Schema(description = "场景相关词汇列表")
    private List<SceneRelatedWordResponse> relatedWords;

    @Schema(description = "词汇列表")
    private List<LearningPlanUnitEntryResponse> words;

    @Schema(description = "生成时间")
    private LocalDateTime generatedTime;

    @Schema(description = "时间")
    private LocalDateTime completedTime;
}
