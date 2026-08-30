package com.chandler.learning.agent.vocabulary.domain.entity;

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

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 英文词汇或短语。 */
    private String term;

    /** 归一化词汇。 */
    private String normalizedTerm;

    /** 词汇学习状态。 */
    private String learningState;

    /** 掌握要求：认识或会拼写。 */
    private String masteryRequirement;

    /** 词义识别掌握分。 */
    private Integer recognitionScore;

    /** 词义识别复习阶段。 */
    private Integer recognitionStage;

    /** 词义识别下次复习时间。 */
    private LocalDateTime recognitionDueTime;

    /** 词义识别答对次数。 */
    private Integer recognitionCorrectCount;

    /** 词义识别答错次数。 */
    private Integer recognitionWrongCount;

    /** 拼写掌握分。 */
    private Integer spellingScore;

    /** 拼写复习阶段。 */
    private Integer spellingStage;

    /** 拼写下次复习时间。 */
    private LocalDateTime spellingDueTime;

    /** 拼写答对次数。 */
    private Integer spellingCorrectCount;

    /** 拼写答错次数。 */
    private Integer spellingWrongCount;

    /** 累计学习曝光次数。 */
    private Integer exposureCount;

    /** 累计参与场景数量。 */
    private Integer sceneCount;

    /** 最近学习计划 ID。 */
    private Long latestPlanId;

    /** 最近学习场景单元 ID。 */
    private Long latestUnitId;

    /** 词卡生成状态。 */
    private String cardStatus;

    /** 最近学习时间。 */
    private LocalDateTime lastLearnedTime;
}
