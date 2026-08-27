package com.chandler.learning.agent.learning.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习计划单元词条及其关联词汇进度数据载体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LearningPlanUnitEntryItem extends LearningPlanUnitEntry {

    private String progressLearningState;
    private Integer progressRecognitionScore;
    private Integer progressSpellingScore;
    private String progressCardStatus;
}
