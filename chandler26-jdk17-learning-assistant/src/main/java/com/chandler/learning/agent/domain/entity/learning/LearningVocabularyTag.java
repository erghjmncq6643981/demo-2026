package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_vocabulary_tag")
public class LearningVocabularyTag {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long vocabularyId;

    private String normalizedTerm;

    private String tagType;

    private String tagValue;

    private String displayName;

    private Integer weight;

    private String source;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
