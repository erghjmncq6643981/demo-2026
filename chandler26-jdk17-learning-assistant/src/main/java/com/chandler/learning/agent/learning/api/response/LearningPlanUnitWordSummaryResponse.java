package com.chandler.learning.agent.learning.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

/**
 * 词汇大挑战日历中的待挑战词汇摘要。
 */
@Data
public class LearningPlanUnitWordSummaryResponse {

    /** 单元词条主键。 */
    @Schema(description = "主键标识")
    private Long id;

    /** 词面。 */
    @Schema(description = "英文词汇")
    private String term;

    /** 音标。 */
    @Schema(description = "音标")
    private String phonetic;

    /** 释义。 */
    @Schema(description = "释义")
    private String meaning;

    /** 场景语境释义。 */
    @Schema(description = "语境释义")
    private String contextMeaning;

    /** 词汇层级，日历摘要只返回核心词。 */
    @Schema(description = "业务属性")
    private String tier;

    /** 认识或会拼写。 */
    @Schema(description = "掌握要求")
    private String masteryRequirement;

    /** 是否已完成挑战。 */
    @Schema(description = "是否已完成挑战")
    private Boolean completed;

    /** 已通过的评测类型列表。 */
    @Schema(description = "已通过的评测列表")
    private java.util.List<String> passedAssessments;
}
