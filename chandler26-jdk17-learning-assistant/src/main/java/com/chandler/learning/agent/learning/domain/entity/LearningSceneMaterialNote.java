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

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 学习计划 ID。 */
    private Long planId;

    /** 学习场景单元 ID。 */
    private Long unitId;

    /** 场景材料 ID。 */
    private Long sceneMaterialId;

    /** 正文内容。 */
    private String content;

    /** 内容格式。 */
    private String contentFormat;
}
