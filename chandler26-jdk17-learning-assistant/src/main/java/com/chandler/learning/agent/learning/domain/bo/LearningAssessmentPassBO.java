package com.chandler.learning.agent.learning.domain.bo;

import lombok.Data;

/**
 * 场景单元内某个词条已经通过的评测类型。
 * 用于一次批量查询后在内存中按词条分组，避免逐词查询复习记录。
 */
@Data
public class LearningAssessmentPassBO {

    /** 场景单元词条对应的个人单词本词条 ID。 */
    private Long entryId;

    /** 已通过的评测类型编码。 */
    private String assessmentType;
}
