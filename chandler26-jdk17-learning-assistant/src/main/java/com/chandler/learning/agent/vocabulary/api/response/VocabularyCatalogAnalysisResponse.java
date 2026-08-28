package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共词本关联分析任务状态。 */
@Data
public class VocabularyCatalogAnalysisResponse {

    /** 分析任务 ID。 */
    @Schema(description = "关联业务标识")
    private Long jobId;
    /** 公共词本 ID。 */
    @Schema(description = "公共词本标识")
    private Long catalogId;
    /** 公共词本版本 ID。 */
    @Schema(description = "词本版本标识")
    private Long catalogVersionId;
    /** 统一 AI 异步任务 ID。 */
    @Schema(description = "异步任务标识")
    private Long asyncTaskId;
    /** 分析修订号。 */
    @Schema(description = "分析规则版本")
    private Integer analysisVersion;
    /** 任务状态。 */
    @Schema(description = "业务状态")
    private String status;
    /** 每批词条数。 */
    @Schema(description = "批处理数量")
    private Integer batchSize;
    /** 本次任务待处理词条数。 */
    @Schema(description = "总数量")
    private Integer totalCount;
    /** 成功分析词条数。 */
    @Schema(description = "数量")
    private Integer successCount;
    /** 失败词条数。 */
    @Schema(description = "失败数量")
    private Integer failedCount;
    /** 语义分组数。 */
    @Schema(description = "数量")
    private Integer groupCount;
    /** 已发布词条总数。 */
    @Schema(description = "数量")
    private Integer publishedCount;
    /** 已有有效分析结果的词条数。 */
    @Schema(description = "数量")
    private Integer analyzedCount;
    /** 尚未产生有效分析结果的词条数。 */
    @Schema(description = "数量")
    private Integer unanalyzedCount;
    /** 当前是否允许触发增量分析。 */
    @Schema(description = "是否满足该条件")
    private Boolean canTrigger;
    /** 任务错误信息。 */
    @Schema(description = "错误信息")
    private String errorMessage;
    /** 开始执行时间。 */
    @Schema(description = "时间")
    private LocalDateTime startedTime;
    /** 完成执行时间。 */
    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;
    /** 任务创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    /** 任务更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
