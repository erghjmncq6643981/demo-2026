package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * AI 生成的场景学习材料及完整结构化结果。
 */
@Data
@TableName("learning_scene_material")
public class LearningSceneMaterial extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long planId;

    private Long unitId;

    /** 单元内材料版本号。 */
    private Integer revisionNo;

    /** 材料状态：draft、published、archived、failed。 */
    private String materialStatus;

    /** 是否为单元当前生效版本。 */
    private Boolean currentVersion;

    /** 上一版本材料 ID。 */
    private Long supersedesMaterialId;

    private Long sessionId;

    private String title;

    private String scenarioType;

    private String learningText;

    private String translation;

    private String rawContent;

    private String parsedJson;

    private String provider;

    private String modelName;

    private Integer tokenUsage;

    private Long costTime;
}
