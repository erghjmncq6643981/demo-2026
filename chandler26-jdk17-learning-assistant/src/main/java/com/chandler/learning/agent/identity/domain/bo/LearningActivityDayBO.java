package com.chandler.learning.agent.identity.domain.bo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 按日期聚合的学习活动查询投影，只承载热力图所需的计数。
 */
@Data
public class LearningActivityDayBO {

    /** 活动日期。 */
    private LocalDate activityDate;

    /** 当日新增或首次学习词条数。 */
    private Integer learnedCount;

    /** 当日复习检查次数。 */
    private Integer reviewCount;
}
