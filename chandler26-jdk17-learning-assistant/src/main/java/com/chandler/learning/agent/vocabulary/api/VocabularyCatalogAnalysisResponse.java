package com.chandler.learning.agent.vocabulary.api;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共词本关联分析任务状态。 */
@Data
public class VocabularyCatalogAnalysisResponse {

    /** 分析任务 ID。 */
    private Long jobId;
    /** 公共词本 ID。 */
    private Long catalogId;
    /** 公共词本版本 ID。 */
    private Long catalogVersionId;
    /** 统一 AI 异步任务 ID。 */
    private Long asyncTaskId;
    /** 分析修订号。 */
    private Integer analysisVersion;
    /** 任务状态。 */
    private String status;
    /** 每批词条数。 */
    private Integer batchSize;
    /** 本次任务待处理词条数。 */
    private Integer totalCount;
    /** 成功分析词条数。 */
    private Integer successCount;
    /** 失败词条数。 */
    private Integer failedCount;
    /** 语义分组数。 */
    private Integer groupCount;
    /** 已发布词条总数。 */
    private Integer publishedCount;
    /** 已有有效分析结果的词条数。 */
    private Integer analyzedCount;
    /** 尚未产生有效分析结果的词条数。 */
    private Integer unanalyzedCount;
    /** 当前是否允许触发增量分析。 */
    private Boolean canTrigger;
    /** 任务错误信息。 */
    private String errorMessage;
    /** 开始执行时间。 */
    private LocalDateTime startedTime;
    /** 完成执行时间。 */
    private LocalDateTime finishedTime;
    /** 任务创建时间。 */
    private LocalDateTime createTime;
    /** 任务更新时间。 */
    private LocalDateTime updateTime;
}
