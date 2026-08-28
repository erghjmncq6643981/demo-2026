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

    /** 词汇层级，日历摘要只返回核心词。 */
    @Schema(description = "业务属性")
    private String tier;

    /** 认识或会拼写。 */
    @Schema(description = "掌握要求")
    private String masteryRequirement;
}
