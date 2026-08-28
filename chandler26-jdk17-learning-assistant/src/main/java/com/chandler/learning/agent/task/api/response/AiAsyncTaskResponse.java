package com.chandler.learning.agent.task.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务中心展示的 AI 异步任务摘要。
 */
@Data
public class AiAsyncTaskResponse {

    @Schema(description = "主键标识")
    private Long id;

    @Schema(description = "用户标识")
    private Long userId;

    /** 任务成果所属用户 ID。 */
    @Schema(description = "所属用户标识")
    private Long ownerUserId;

    /** 发起任务的用户 ID。 */
    @Schema(description = "关联业务标识")
    private Long triggerUserId;

    @Schema(description = "触发人名称")
    private String triggerUserName;

    /** 最近一次继续或干预任务的用户 ID。 */
    @Schema(description = "操作人用户标识")
    private Long operatorUserId;

    @Schema(description = "操作人名称")
    private String operatorUserName;

    @Schema(description = "触发方式")
    private String triggerType;

    @Schema(description = "可见范围")
    private String visibility;

    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "名称")
    private String taskName;

    @Schema(description = "学习计划标识")
    private Long planId;

    @Schema(description = "场景单元标识")
    private Long unitId;

    @Schema(description = "关联任务标识")
    private Long relatedJobId;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务标识")
    private String businessId;

    @Schema(description = "业务状态")
    private String status;

    @Schema(description = "执行方式")
    private String executionMode;

    @Schema(description = "时间")
    private LocalDateTime scheduledTime;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "总数量")
    private Integer totalCount;

    @Schema(description = "成功数量")
    private Integer successCount;

    @Schema(description = "数量")
    private Integer failedCount;

    @Schema(description = "进度百分比")
    private Integer progressPercent;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "最大重试次数")
    private Integer maxRetryCount;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "开始时间")
    private LocalDateTime startedTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishedTime;

    @Schema(description = "取消时间")
    private LocalDateTime cancelledTime;

    @Schema(description = "时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /** 详情接口返回的可恢复步骤，摘要列表可为空。 */
    @Schema(description = "任务步骤列表")
    private List<AiAsyncTaskStepResponse> steps;
}
