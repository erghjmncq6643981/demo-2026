package com.chandler.learning.agent.learning.domain.bo;

import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景单元及其当前有效场景材料数据载体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningPlanUnitItem extends LearningPlanUnit {

    /** 场景材料 ID。 */
    private Long materialId;
    /** 场景材料英文正文。 */
    private String materialLearningText;
    /** 场景材料中文译文。 */
    private String materialTranslation;
    /** 场景材料结构化 JSON。 */
    private String materialParsedJson;
    /** 场景材料修订版本号。 */
    private Integer materialRevisionNo;
}
