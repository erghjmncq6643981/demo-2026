package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 任务中心展示的可恢复步骤。 */
@Data
public class AiAsyncTaskStepResponse {

    private Long id;
    private String stepCode;
    private String stepName;
    private Integer stepOrder;
    private String status;
    private Integer completedCount;
    private Integer totalCount;
    private Integer attemptCount;
    private Integer maxAttemptCount;
    private String errorMessage;
    private LocalDateTime heartbeatTime;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private List<AiAsyncTaskAttemptResponse> attempts;
}
