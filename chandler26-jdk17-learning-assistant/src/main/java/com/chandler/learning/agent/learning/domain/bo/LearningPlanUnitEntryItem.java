package com.chandler.learning.agent.learning.domain.bo;

import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnitEntry;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习计划单元词条及其关联词汇进度数据载体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningPlanUnitEntryItem extends LearningPlanUnitEntry {

    /** 词汇学习进度状态。 */
    private String progressLearningState;
    /** 词义识别掌握分。 */
    private Integer progressRecognitionScore;
    /** 拼写掌握分。 */
    private Integer progressSpellingScore;
    /** 词卡生成进度状态。 */
    private String progressCardStatus;
}
