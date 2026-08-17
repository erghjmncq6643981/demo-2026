package com.chandler.learning.agent.domain.entity.vocabulary;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.domain.entity.BaseEntity;
import lombok.Data;

/**
 * 批量词卡任务中的单词级结果，支持仅重试失败项。
 */
@Data
@TableName("vocabulary_card_generation_job_item")
public class VocabularyCardGenerationJobItem extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long jobId;

    private Long wordProgressId;

    private Long wordbookEntryId;

    private String term;

    private String normalizedTerm;

    private String status;

    private Long vocabularyId;

    private Integer attemptCount;

    private String errorMessage;
}
