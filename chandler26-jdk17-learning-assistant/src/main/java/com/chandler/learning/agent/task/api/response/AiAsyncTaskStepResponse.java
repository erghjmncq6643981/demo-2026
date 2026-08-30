package com.chandler.learning.agent.task.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 任务中心展示的可恢复步骤。 */
@Data
public class AiAsyncTaskStepResponse {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "步骤编码")
    private String stepCode;
    @Schema(description = "步骤名称")
    private String stepName;
    @Schema(description = "步骤序号")
    private Integer stepOrder;
    @Schema(description = "当前业务状态")
    private String status;
    @Schema(description = "已完成数量")
    private Integer completedCount;
    @Schema(description = "任务或分页数据总数")
    private Integer totalCount;
    @Schema(description = "尝试次数")
    private Integer attemptCount;
    @Schema(description = "最大尝试次数")
    private Integer maxAttemptCount;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "心跳时间")
    private LocalDateTime heartbeatTime;
    @Schema(description = "开始时间")
    private LocalDateTime startedTime;
    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;
    @Schema(description = "执行尝试列表")
    private List<AiAsyncTaskAttemptResponse> attempts;
}
