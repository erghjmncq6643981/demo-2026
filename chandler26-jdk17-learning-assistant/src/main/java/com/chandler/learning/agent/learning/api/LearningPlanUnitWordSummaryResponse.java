package com.chandler.learning.agent.learning.api;

import lombok.Data;

/**
 * 词汇大挑战日历中的待挑战词汇摘要。
 */
@Data
public class LearningPlanUnitWordSummaryResponse {

    /** 单元词条主键。 */
    private Long id;

    /** 词面。 */
    private String term;

    /** 词汇层级，日历摘要只返回核心词。 */
    private String tier;

    /** 认识或会拼写。 */
    private String masteryRequirement;
}
