package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
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
