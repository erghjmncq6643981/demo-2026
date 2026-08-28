package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词汇标签 DO。
 */
@Data
@TableName("learning_vocabulary_tag")
public class LearningVocabularyTag extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 公共词汇缓存 ID。
     */
    @Schema(description = "公共词汇缓存 ID")
    private Long vocabularyId;

    /**
     * 归一化单词或短语。
     */
    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    /**
     * 标签类型。
     */
    @Schema(description = "标签类型")
    private String tagType;

    /**
     * 标签值，用于检索和关联。
     */
    @Schema(description = "标签值，用于检索和关联")
    private String tagValue;

    /**
     * 标签展示名称。
     */
    @Schema(description = "标签展示名称")
    private String displayName;

    /**
     * 标签权重，数值越大越重要。
     */
    @Schema(description = "标签权重，数值越大越重要")
    private Integer weight;

    /**
     * 标签来源。
     */
    @Schema(description = "标签来源")
    private String source;
}
