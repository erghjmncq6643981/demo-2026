package com.chandler.learning.agent.domain.entity.vocabulary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量 AI 词卡生成任务。
 */
@Data
@TableName("vocabulary_card_generation_job")
public class VocabularyCardGenerationJob extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long planId;

    private Long unitId;

    private String status;

    private Integer batchSize;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
