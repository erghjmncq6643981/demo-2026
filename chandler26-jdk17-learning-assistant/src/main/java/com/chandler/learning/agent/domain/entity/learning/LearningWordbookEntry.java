package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_wordbook_entry")
public class LearningWordbookEntry {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long wordbookId;

    private Long vocabularyId;

    private String term;

    private String normalizedTerm;

    private String note;

    private Integer reviewStage;

    private Integer masteryScore;

    private LocalDateTime firstReviewTime;

    private LocalDateTime lastReviewTime;

    private LocalDateTime nextReviewTime;

    private Integer dueCount;

    private Integer reviewCount;

    private Integer correctCount;

    private Integer wrongCount;

    private Boolean deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
