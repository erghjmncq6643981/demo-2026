package com.chandler.learning.agent.learning.domain.bo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场景词汇挑战单题检查上下文业务聚合对象。
 * 用于通过一条 JOIN SQL 一次性加载计划、单元、词条、生词本与进度数据。
 */
@Data
public class LearningAssessmentContextBO {

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习者用户 ID。 */
    private Long userId;

    /** 学习计划名称。 */
    private String planName;

    /** 个人单词本 ID。 */
    private Long wordbookId;

    /** 场景学习单元 ID。 */
    private Long unitId;

    /** 场景单元标题。 */
    private String unitTitle;

    /** 场景核心词数量。 */
    private Integer coreWordCount;

    /** 场景已完成核心词数量。 */
    private Integer completedCoreCount;

    /** 场景单元状态。 */
    private String unitStatus;

    /** 场景单元词条 ID。 */
    private Long unitEntryId;

    /** 词汇分层：核心、扩展或补充。 */
    private String tier;

    /** 掌握要求：认识或会拼写。 */
    private String masteryRequirement;

    /** 英文词汇或短语。 */
    private String term;

    /** 归一化词汇。 */
    private String normalizedTerm;

    /** 词汇评测题目配置 JSON。 */
    private String assessmentJson;

    /** 允许拼写列表 JSON。 */
    private String acceptedSpellingsJson;

    /** 是否首次学习。 */
    private Boolean firstLearning;

    /** 个人单词本词条 ID。 */
    private Long wordbookEntryId;

    /** 逐词学习进度 ID。 */
    private Long wordProgressId;

    /** 个人单词本复习阶段。 */
    private Integer wordbookStage;

    /** 个人单词本熟练度分数。 */
    private Integer wordbookMastery;

    /** 下次复习时间。 */
    private LocalDateTime wordbookNextReviewTime;

    /** 词汇学习进度状态。 */
    private String progressLearningState;

    /** 词义识别掌握分。 */
    private Integer progressRecognitionScore;

    /** 拼写掌握分。 */
    private Integer progressSpellingScore;

    /** 词义识别复习阶段。 */
    private Integer progressRecognitionStage;

    /** 拼写复习阶段。 */
    private Integer progressSpellingStage;

    /** 词义识别正确次数。 */
    private Integer progressRecognitionCorrectCount;

    /** 拼写正确次数。 */
    private Integer progressSpellingCorrectCount;

    /** 词义识别错误次数。 */
    private Integer progressRecognitionWrongCount;

    /** 拼写错误次数。 */
    private Integer progressSpellingWrongCount;
}
