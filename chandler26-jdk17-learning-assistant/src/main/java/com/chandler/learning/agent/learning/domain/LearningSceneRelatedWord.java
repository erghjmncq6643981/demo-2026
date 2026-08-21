package com.chandler.learning.agent.learning.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 与具体材料版本关联的场景扩展词；浏览本记录不会创建个人学习进度。
 */
@Data
@TableName("learning_scene_related_word")
public class LearningSceneRelatedWord extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long planId;
    private Long unitId;
    private Long sceneMaterialId;
    private String term;
    private String normalizedTerm;
    private String phonetic;
    private String meaningText;
    private String contextMeaning;
    private String categoryCode;
    private String categoryName;
    private String sourceType;
    private Integer sortOrder;
    private Boolean promoted;
    private Long promotedEntryId;
}
