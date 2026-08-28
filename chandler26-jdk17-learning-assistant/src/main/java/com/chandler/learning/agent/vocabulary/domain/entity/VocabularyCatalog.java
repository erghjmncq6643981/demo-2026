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

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerUserId;

    private String name;

    private String learningPurpose;

    private String examType;

    private Long latestVersionId;

    private String status;

    private String visibility;
}
