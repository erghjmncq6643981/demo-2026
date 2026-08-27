package com.chandler.learning.agent.learning.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景单元及其当前有效场景材料数据载体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningPlanUnitItem extends LearningPlanUnit {

    private Long materialId;
    private String materialLearningText;
    private String materialTranslation;
    private String materialParsedJson;
    private Integer materialRevisionNo;
}
