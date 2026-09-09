package com.chandler.learning.agent.learning.domain.bo;

import lombok.Data;

import java.time.LocalDate;

/** 活动日汇总查询投影，只承载热力图和学习摘要所需指标。 */
@Data
public class LearningActivityMetricBO {

    /** 活动日期。 */
    private LocalDate activityDate;

    /** 指标编码。 */
    private String metricType;

    /** 指标数量。 */
    private Integer metricCount;

    /** 有效学习时长，单位秒。 */
    private Integer durationSeconds;

    /** 指标中的正确数量。 */
    private Integer correctCount;

    /** 指标总尝试数量。 */
    private Integer totalCount;
}
