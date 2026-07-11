package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词汇关联关系 DO。
 */
@Data
@TableName("learning_vocabulary_relation")
public class LearningVocabularyRelation extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "当前词汇缓存 ID")
    private Long vocabularyId;

    @Schema(description = "关联词已入库时的词汇缓存 ID")
    private Long relatedVocabularyId;

    @Schema(description = "当前词归一化值")
    private String normalizedTerm;

    @Schema(description = "关联单词或短语")
    private String relatedTerm;

    @Schema(description = "关联类型：synonym/antonym/word_family/tag_overlap")
    private String relationType;

    @Schema(description = "关联说明或共享标签")
    private String relationValue;

    @Schema(description = "关联词核心词性")
    private String relatedPartOfSpeech;

    @Schema(description = "关联词核心含义")
    private String relatedMeaning;

    @Schema(description = "匹配来源")
    private String matchType;

    @Schema(description = "匹配分数")
    private Integer matchScore;

    @Schema(description = "相关度分数")
    private Integer score;

    @Schema(description = "关联来源")
    private String source;
}
