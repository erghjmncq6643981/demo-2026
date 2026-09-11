package com.chandler.learning.agent.learning.domain.bo;

import lombok.Data;

/**
 * 学习计划已生成核心词计数业务对象。
 */
@Data
public class LearningPlanGeneratedCountBO {

    /** 学习计划标识。 */
    private Long planId;

    /** 已生成场景的核心词总数。 */
    private Integer generatedCoreCount;
}
