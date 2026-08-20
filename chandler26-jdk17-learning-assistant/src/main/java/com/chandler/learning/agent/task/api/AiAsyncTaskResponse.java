package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务中心展示的 AI 异步任务摘要。
 */
@Data
public class AiAsyncTaskResponse {

    private Long id;

    private Long userId;

    private String userName;

    private String taskType;

    private String taskName;

    private Long planId;

    private Long unitId;

    private Long relatedJobId;

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
}
