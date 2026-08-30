package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/** 公共词本语义分析的批次明细，支持只重试失败批次。 */
@Data
@TableName("vocabulary_catalog_analysis_batch")
public class VocabularyCatalogAnalysisBatch extends BaseEntity {

    /** 批次主键。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属分析任务 ID。 */
    private Long jobId;

    /** 任务内的批次序号。 */
    private Integer batchNo;

    /** 当前批次词条数。 */
    private Integer entryCount;

    /** 当前批次词条 ID JSON 数组。 */
    private String entryIdsJson;

    /** 批次状态。 */
    private String status;

    /** 已尝试次数。 */
    private Integer attemptCount;

    /** 最近一次批次错误信息。 */
    private String errorMessage;

    /** 批次开始时间。 */
    private LocalDateTime startedTime;

    /** 批次完成时间。 */
    private LocalDateTime finishedTime;
}
