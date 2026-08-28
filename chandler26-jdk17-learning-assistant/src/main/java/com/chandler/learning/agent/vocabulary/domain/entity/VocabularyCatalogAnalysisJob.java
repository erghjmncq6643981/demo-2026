package com.chandler.learning.agent.vocabulary.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chandler.learning.agent.common.persistence.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共词本版本的语义索引分析任务。
 * <p>
 * 分析结果按任务版本保存，重新分析不会覆盖历史快照。
 */
@Data
@TableName("vocabulary_catalog_analysis_job")
public class VocabularyCatalogAnalysisJob extends BaseEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    /** 分析任务主键。 */
    private Long id;

    /** 发起任务的用户 ID。 */
    private Long userId;

    /** 公共词本 ID。 */
    private Long catalogId;

    /** 待分析的公共词本版本 ID。 */
    private Long catalogVersionId;

    /** 关联的统一 AI 异步任务 ID。 */
    private Long asyncTaskId;

    /** 词本版本内的分析修订号。 */
    private Integer analysisVersion;

    /** 任务状态。 */
    private String status;

    /** 每次 AI 调用处理的词条数。 */
    private Integer batchSize;

    /** 本次任务待处理词条数。 */
    private Integer totalCount;

    /** 成功分析词条数。 */
    private Integer successCount;

    /** 失败词条数。 */
    private Integer failedCount;

    /** 已识别的语义分组数。 */
    private Integer groupCount;

    /** 最近一次任务错误信息。 */
    private String errorMessage;

    /** 开始执行时间。 */
    private LocalDateTime startedTime;

    /** 完成执行时间。 */
    private LocalDateTime finishedTime;
}
