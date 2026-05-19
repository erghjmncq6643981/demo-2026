package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_vocabulary_relation")
public class LearningVocabularyRelation {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long vocabularyId;

    private Long relatedVocabularyId;

    private String normalizedTerm;

    private String relatedTerm;

    private String relationType;

    private String relationValue;

    private String relatedPartOfSpeech;

    private String relatedMeaning;

    private String matchType;

    private Integer matchScore;

    private Integer score;

    private String source;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
