package com.chandler.learning.agent.learning.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 学习者针对一篇场景材料记录的 Markdown 笔记。
 */
@Data
@TableName("learning_scene_material_note")
public class LearningSceneMaterialNote extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long planId;

    private Long unitId;

    private Long sceneMaterialId;

    private String content;

    private String contentFormat;
}
