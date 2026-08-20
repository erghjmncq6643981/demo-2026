package com.chandler.learning.agent.vocabulary.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

/**
 * 批量词卡任务中的单词级结果，支持仅重试失败项。
 */
@Data
@TableName("vocabulary_card_generation_job_item")
public class VocabularyCardGenerationJobItem extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    /** 任务明细主键。 */
    private Long id;

    /** 所属词卡任务 ID。 */
    private Long jobId;

    /** 用户逐词进度 ID。 */
    private Long wordProgressId;

    /** 需要写回词卡快照的个人词条 ID。 */
    private Long wordbookEntryId;

    /** 展示单词。 */
    private String term;

    /** 归一化单词，用于去重。 */
    private String normalizedTerm;

    /** 明细状态。 */
    private String status;

    /** 共享词卡缓存 ID。 */
    private Long vocabularyId;

    /** 已尝试生成次数。 */
    private Integer attemptCount;

    /** 明细失败原因。 */
    private String errorMessage;
}
