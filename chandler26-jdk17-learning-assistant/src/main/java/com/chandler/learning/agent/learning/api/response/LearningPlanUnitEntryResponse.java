package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.List;

/**
 * 场景单元词汇响应。
 */
@Data
public class LearningPlanUnitEntryResponse {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "公共词本词条标识")
    private Long catalogEntryId;

    @Schema(description = "单词本词条标识")
    private Long wordbookEntryId;

    @Schema(description = "词汇学习进度 ID")
    private Long wordProgressId;

    @Schema(description = "来源序号")
    private Integer sourceOrder;

    @Schema(description = "英文词汇")
    private String term;

    @Schema(description = "标准化词汇")
    private String normalizedTerm;

    @Schema(description = "音标")
    private String phonetic;

    @Schema(description = "释义")
    private String meaning;

    @Schema(description = "语境释义")
    private String contextMeaning;

    @Schema(description = "词汇在场景中的层级")
    private String tier;

    @Schema(description = "掌握要求")
    private String masteryRequirement;

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Schema(description = "可接受拼写")
    private String acceptedSpellings;

    @com.fasterxml.jackson.annotation.JsonRawValue
    @Schema(description = "词汇检查内容")
    private String assessment;

    @Schema(description = "已通过的评测类型列表")
    private List<String> passedAssessments;

    @Schema(description = "是否首次学习")
    private Boolean firstLearning;

    @Schema(description = "学习状态")
    private String learningState;

    @Schema(description = "词义识别得分")
    private Integer recognitionScore;

    @Schema(description = "拼写得分")
    private Integer spellingScore;

    @Schema(description = "词卡生成状态")
    private String cardStatus;
}
