package com.chandler.learning.agent.vocabulary.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词汇关联关系 DO。
 */
@Data
@TableName("learning_vocabulary_relation")
public class LearningVocabularyRelation extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 当前词汇缓存 ID。
     */
    @Schema(description = "当前词汇缓存 ID")
    private Long vocabularyId;

    /**
     * 关联词已入库时的词汇缓存 ID。
     */
    @Schema(description = "关联词已入库时的词汇缓存 ID")
    private Long relatedVocabularyId;

    /**
     * 当前词归一化值。
     */
    @Schema(description = "当前词归一化值")
    private String normalizedTerm;

    /**
     * 关联单词或短语。
     */
    @Schema(description = "关联单词或短语")
    private String relatedTerm;

    /**
     * 关联类型：synonym/antonym/word_family/tag_overlap。
     */
    @Schema(description = "关联类型：synonym/antonym/word_family/tag_overlap")
    private String relationType;

    /**
     * 关联说明或共享标签。
     */
    @Schema(description = "关联说明或共享标签")
    private String relationValue;

    /**
     * 关联词核心词性。
     */
    @Schema(description = "关联词核心词性")
    private String relatedPartOfSpeech;

    /**
     * 关联词核心含义。
     */
    @Schema(description = "关联词核心含义")
    private String relatedMeaning;

    /**
     * 匹配来源。
     */
    @Schema(description = "匹配来源")
    private String matchType;

    /**
     * 匹配分数。
     */
    @Schema(description = "匹配分数")
    private Integer matchScore;

    /**
     * 相关度分数。
     */
    @Schema(description = "相关度分数")
    private Integer score;

    /**
     * 关联来源。
     */
    @Schema(description = "关联来源")
    private String source;
}
