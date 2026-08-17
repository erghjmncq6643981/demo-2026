package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

/**
 * 基于一个已发布词表版本的场景化自助学习计划。
 */
@Data
@TableName("learning_plan")
public class LearningPlan extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long catalogId;

    private Long catalogVersionId;

    private Long wordbookId;

    private String name;

    private String learningPurpose;

    private String status;

    private Integer totalCatalogWords;

    private Integer learnedCoreWords;

    private Integer completedUnitCount;

    private Long currentUnitId;

    private Long aiSessionId;
}
