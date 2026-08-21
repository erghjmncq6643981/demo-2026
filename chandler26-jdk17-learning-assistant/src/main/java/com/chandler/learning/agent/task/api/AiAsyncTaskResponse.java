package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务中心展示的 AI 异步任务摘要。
 */
@Data
public class AiAsyncTaskResponse {

    private Long id;

    private Long userId;

    /** 任务成果所属用户 ID。 */
    private Long ownerUserId;

    /** 发起任务的用户 ID。 */
    private Long triggerUserId;

    private String triggerUserName;

    /** 最近一次继续或干预任务的用户 ID。 */
    private Long operatorUserId;

    private String operatorUserName;

    private String triggerType;

    private String visibility;

    private String userName;

    private String taskType;

    private String taskName;

    private Long planId;

    private Long unitId;

    private Long relatedJobId;

    private String businessType;

    private String businessId;

    private String status;

    private String executionMode;

    private LocalDateTime scheduledTime;

    private Integer priority;

    private Integer totalCount;

    private Integer successCount;

    private Integer failedCount;

    private Integer progressPercent;

    private Integer retryCount;

    private Integer maxRetryCount;

    private String errorMessage;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime cancelledTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 详情接口返回的可恢复步骤，摘要列表可为空。 */
    private List<AiAsyncTaskStepResponse> steps;
}
