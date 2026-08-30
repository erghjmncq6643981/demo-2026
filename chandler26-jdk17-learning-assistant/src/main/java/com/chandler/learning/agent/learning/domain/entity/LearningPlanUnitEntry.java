package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 场景单元中的核心、扩展、补充或复习词汇。
 */
@Data
@TableName("learning_plan_unit_entry")
public class LearningPlanUnitEntry extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** 公共词本词条 ID。 */
    private Long catalogEntryId;

    /** 个人单词本词条 ID。 */
    private Long wordbookEntryId;

    /** 词汇学习进度 ID。 */
    private Long wordProgressId;

    /** 词条在源文件中的序号。 */
    private Integer sourceOrder;

    /** 英文词汇或短语。 */
    private String term;

    /** 归一化词汇。 */
    private String normalizedTerm;

    /** 词汇音标。 */
    private String phonetic;

    /** 词汇中文释义。 */
    private String meaningText;

    /** 当前语境中的词义。 */
    private String contextMeaning;

    /** 词汇在场景中的层级。 */
    private String tier;

    /** 掌握要求：认识或会拼写。 */
    private String masteryRequirement;

    /** 允许拼写列表 JSON。 */
    private String acceptedSpellingsJson;

    /** 词汇评测配置 JSON。 */
    private String assessmentJson;

    /** 是否首次学习。 */
    private Boolean firstLearning;

    /** 展示排序号。 */
    private Integer sortOrder;
}
