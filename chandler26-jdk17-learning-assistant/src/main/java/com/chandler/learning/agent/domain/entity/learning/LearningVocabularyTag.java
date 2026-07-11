package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词汇标签 DO。
 */
@Data
@TableName("learning_vocabulary_tag")
public class LearningVocabularyTag extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "公共词汇缓存 ID")
    private Long vocabularyId;

    @Schema(description = "归一化单词或短语")
    private String normalizedTerm;

    @Schema(description = "标签类型")
    private String tagType;

    @Schema(description = "标签值，用于检索和关联")
    private String tagValue;

    @Schema(description = "标签展示名称")
    private String displayName;

    @Schema(description = "标签权重，数值越大越重要")
    private Integer weight;

    @Schema(description = "标签来源")
    private String source;
}
