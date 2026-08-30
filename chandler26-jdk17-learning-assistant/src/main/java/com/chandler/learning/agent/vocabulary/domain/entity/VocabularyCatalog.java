package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 可导入并生成学习计划的词表。
 */
@Data
@TableName("vocabulary_catalog")
public class VocabularyCatalog extends BaseEntity {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 公共词本所有者用户 ID。 */
    private Long ownerUserId;

    /** 业务对象名称。 */
    private String name;

    /** 学习目标与用途说明。 */
    private String learningPurpose;

    /** 考试或词表来源类型。 */
    private String examType;

    /** 最新公共词本版本 ID。 */
    private Long latestVersionId;

    /** 当前业务状态。 */
    private String status;

    /** 数据可见范围。 */
    private String visibility;
}
