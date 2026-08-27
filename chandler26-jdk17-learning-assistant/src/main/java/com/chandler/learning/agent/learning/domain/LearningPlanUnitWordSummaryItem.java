package com.chandler.learning.agent.learning.domain;

import lombok.Data;

/**
 * 日历摘要使用的单元词汇载体，只包含词面和掌握要求，不携带词卡或题目 JSON。
 */
@Data
public class LearningPlanUnitWordSummaryItem {

    /** 单元词条主键。 */
    private Long id;

    /** 场景单元主键。 */
    private Long unitId;

    /** 词本词条主键，用于关联评测记录。 */
    private Long wordbookEntryId;

    /** 词面。 */
    private String term;

    /** 词汇层级。 */
    private String tier;

    /** 掌握要求：认识或会拼写。 */
    private String masteryRequirement;
}
