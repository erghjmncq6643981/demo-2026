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

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long planId;

    private Long unitId;

    private Long catalogEntryId;

    private Long wordbookEntryId;

    private Long wordProgressId;

    private Integer sourceOrder;

    private String term;

    private String normalizedTerm;

    private String phonetic;

    private String meaningText;

    private String contextMeaning;

    private String tier;

    private String masteryRequirement;

    private String acceptedSpellingsJson;

    private String assessmentJson;

    private Boolean firstLearning;

    private Integer sortOrder;
}
