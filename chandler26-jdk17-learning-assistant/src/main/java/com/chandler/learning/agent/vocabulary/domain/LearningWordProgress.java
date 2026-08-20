package com.chandler.learning.agent.vocabulary.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户跨词本共享的逐词认读与拼写进度。
 */
@Data
@TableName("learning_word_progress")
public class LearningWordProgress extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String term;

    private String normalizedTerm;

    private String learningState;

    private String masteryRequirement;

    private Integer recognitionScore;

    private Integer recognitionStage;

    private LocalDateTime recognitionDueTime;

    private Integer recognitionCorrectCount;

    private Integer recognitionWrongCount;

    private Integer spellingScore;

    private Integer spellingStage;

    private LocalDateTime spellingDueTime;

    private Integer spellingCorrectCount;

    private Integer spellingWrongCount;

    private Integer exposureCount;

    private Integer sceneCount;

    private Long latestPlanId;

    private Long latestUnitId;

    private String cardStatus;

    private LocalDateTime lastLearnedTime;
}
