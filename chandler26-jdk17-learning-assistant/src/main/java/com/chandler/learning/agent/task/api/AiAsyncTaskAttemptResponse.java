package com.chandler.learning.agent.task.api;

import lombok.Data;

import java.time.LocalDateTime;

/** AI 任务步骤单次执行摘要。 */
@Data
public class AiAsyncTaskAttemptResponse {

    private Long id;
    private Long operatorUserId;
    private String operatorUserName;
    private Integer attemptNo;
    private String status;
    private Long modelConfigId;
    private String provider;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long costTime;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
}
