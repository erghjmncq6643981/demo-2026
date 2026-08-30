package com.chandler.learning.agent.learning.domain.entity;

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
    /** 英文词汇或短语。 */
    private String term;
    /** 归一化词汇。 */
    private String normalizedTerm;
    /** 词汇音标。 */
    private String phonetic;
    /** 词汇中文释义。 */
    private String meaningText;
    /** 当前语境中的词义。 */
    private String contextMeaning;
    /** 场景词分类编码。 */
    private String categoryCode;
    /** 场景词分类名称。 */
    private String categoryName;
    /** 数据来源类型。 */
    private String sourceType;
    /** 展示排序号。 */
    private Integer sortOrder;
    /** 是否已提升为核心挑战词。 */
    private Boolean promoted;
    /** 提升后生成的核心词条 ID。 */
    private Long promotedEntryId;
}
