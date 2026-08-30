package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 英语词汇形态变形与别名索引 DO。
 */
@Data
@TableName("learning_vocabulary_alias")
public class LearningVocabularyAlias extends BaseEntity {

    /**
     * 主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private Long id;

    /**
     * 关联的公共词汇缓存主键 ID。
     */
    @Schema(description = "公共词汇缓存主键 ID")
    private Long vocabularyId;

    /**
     * 变形或别名单词（如 running, went, apples）。
     */
    @Schema(description = "变形或别名单词")
    private String aliasTerm;

    /**
     * 归一化别名。
     */
    @Schema(description = "归一化别名")
    private String normalizedAlias;

    /**
     * 词卡主词或原型词（如 run, go, apple）。
     */
    @Schema(description = "原型词")
    private String lemma;

    /**
     * 归一化原型词。
     */
    @Schema(description = "归一化原型词")
    private String normalizedLemma;

    /**
     * 别名类型：exact, plural, past_tense, past_participle, present_participle, third_person_singular, comparative, superlative, irregular, ai_generated 等。
     */
    @Schema(description = "别名类型")
    private String aliasType;

    /**
     * 来源：rule, ai, manual。
     */
    @Schema(description = "来源")
    private String source;
}
