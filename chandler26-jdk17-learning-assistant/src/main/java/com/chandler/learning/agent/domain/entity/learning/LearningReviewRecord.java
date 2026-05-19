package com.chandler.learning.agent.domain.entity.learning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_review_record")
public class LearningReviewRecord {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long wordbookId;

    private Long entryId;

    private Long vocabularyId;

    private String normalizedTerm;

    private String result;

    private Integer score;

    private Integer reviewStageBefore;

    private Integer reviewStageAfter;

    private Integer masteryBefore;

    private Integer masteryAfter;

    private LocalDateTime nextReviewTime;

    private Integer durationSeconds;

    private LocalDateTime createTime;
}
