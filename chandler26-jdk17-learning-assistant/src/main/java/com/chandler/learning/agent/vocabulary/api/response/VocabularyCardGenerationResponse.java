package com.chandler.learning.agent.vocabulary.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量词卡任务结果。
 */
@Data
public class VocabularyCardGenerationResponse {

    @Schema(description = "关联业务标识")
    private Long jobId;

    @Schema(description = "学习计划标识")
    private Long planId;

    @Schema(description = "场景单元标识")
    private Long unitId;

    @Schema(description = "异步任务标识")
    private Long asyncTaskId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "批处理数量")
    private Integer batchSize;

    @Schema(description = "总数量")
    private Integer totalCount;

    @Schema(description = "成功数量")
    private Integer successCount;

    @Schema(description = "数量")
    private Integer failedCount;

    /** 任务级失败原因；单词级失败原因位于 items。 */
    @Schema(description = "错误信息")
    private String errorMessage;

    /** 当前返回的明细页码，从 1 开始。 */
    @Schema(description = "词条页码")
    private Integer itemPage;

    /** 当前返回的明细页大小。 */
    @Schema(description = "词条每页数量")
    private Integer itemPageSize;

    /** 任务明细总数。 */
    @Schema(description = "词条总数量")
    private Long itemTotal;

    /** 是否还有未返回的任务明细。 */
    @Schema(description = "是否还有更多词条")
    private Boolean itemHasMore;

    @Schema(description = "列表数据")
    private List<VocabularyCardGenerationItemResponse> items;

    @Schema(description = "开始时间")
    private LocalDateTime startedTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;
}
